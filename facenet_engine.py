import sys
import json
import os
import torch
import numpy as np
import cv2
from PIL import Image, ImageOps
from facenet_pytorch import MTCNN, InceptionResnetV1

# ---------------------------------------------------------
# Device Configuration
# ---------------------------------------------------------
device = torch.device('cuda:0' if torch.cuda.is_available() else 'cpu')

# ---------------------------------------------------------
# Initialize Neural Detection and Recognition Models
# ---------------------------------------------------------
mtcnn = MTCNN(
    image_size=160,
    margin=14,
    min_face_size=20,
    thresholds=[0.6, 0.7, 0.7],  # Strict thresholds to reject non-face objects
    post_process=True,
    keep_all=False,
    device=device
)

resnet = InceptionResnetV1(pretrained='vggface2').eval().to(device)


def _load_corrected_image(image_path):
    """Loads image and adjusts EXIF orientation metadata."""
    img = Image.open(image_path)
    img = ImageOps.exif_transpose(img)
    return img.convert('RGB')


def get_face_embedding_with_meta(image_path):
    """
    Strictly detects whether a human face is present before extracting vectors.
    Returns:
        (embedding_list, bbox_list, detection_confidence) on success
        (None, None, 0.0) if no face is detected
    """
    if not os.path.isfile(image_path):
        return None, None, 0.0

    try:
        img = _load_corrected_image(image_path)
    except Exception:
        return None, None, 0.0

    # ---------------------------------------------------------
    # Stage 1: Primary Face Detection via MTCNN
    # ---------------------------------------------------------
    boxes, probs = None, None
    try:
        boxes, probs = mtcnn.detect(img)
    except Exception:
        pass

    # Strict check: Require MTCNN bounding box with confidence >= 70%
    if boxes is not None and len(boxes) > 0 and probs is not None and len(probs) > 0:
        if probs[0] is not None and float(probs[0]) >= 0.70:
            b = boxes[0]
            bbox = [int(b[0]), int(b[1]), int(b[2] - b[0]), int(b[3] - b[1])]
            det_conf = round(float(probs[0]) * 100.0, 2)

            try:
                face_tensor = mtcnn(img)
                if face_tensor is not None:
                    face_tensor = face_tensor.unsqueeze(0).to(device)
                    with torch.no_grad():
                        embedding = resnet(face_tensor).detach().cpu().numpy()[0]

                    norm = np.linalg.norm(embedding)
                    if norm > 0:
                        embedding = embedding / norm

                    return embedding.tolist(), bbox, det_conf
            except Exception:
                pass

    # ---------------------------------------------------------
    # Stage 2: Secondary Fallback via OpenCV Haar Cascade
    # ---------------------------------------------------------
    try:
        cascade_file = os.path.join(cv2.data.haarcascades, 'haarcascade_frontalface_default.xml')
        if os.path.exists(cascade_file):
            face_cascade = cv2.CascadeClassifier(cascade_file)
            np_img = np.array(img)
            gray = cv2.cvtColor(np_img, cv2.COLOR_RGB2GRAY)
            faces = face_cascade.detectMultiScale(
                gray,
                scaleFactor=1.1,
                minNeighbors=5,
                minSize=(45, 45)
            )

            if len(faces) > 0:
                x, y, w, h = max(faces, key=lambda b: b[2] * b[3])
                bbox = [int(x), int(y), int(w), int(h)]
                det_conf = 85.0

                cropped = np_img[max(0, y):min(np_img.shape[0], y + h), max(0, x):min(np_img.shape[1], x + w)]
                resized = cv2.resize(cropped, (160, 160))
                tensor = torch.tensor(resized, dtype=torch.float32).permute(2, 0, 1)
                tensor = (tensor - 127.5) / 128.0
                tensor = tensor.unsqueeze(0).to(device)

                with torch.no_grad():
                    embedding = resnet(tensor).detach().cpu().numpy()[0]

                norm = np.linalg.norm(embedding)
                if norm > 0:
                    embedding = embedding / norm

                return embedding.tolist(), bbox, det_conf
    except Exception:
        pass

    # No valid human face detected by either detector
    return None, None, 0.0


def compute_similarity(emb1, emb2):
    """Computes cosine similarity between two unit-normalized 512-D vectors."""
    v1 = np.array(emb1, dtype=np.float32)
    v2 = np.array(emb2, dtype=np.float32)
    dot = float(np.dot(v1, v2))
    return round(max(0.0, dot) * 100.0, 2)


# ---------------------------------------------------------
# CLI Command Dispatcher
# ---------------------------------------------------------
if __name__ == '__main__':
    if len(sys.argv) < 3:
        print(json.dumps({"status": "ERROR", "message": "Missing command arguments."}))
        sys.exit(1)

    mode = sys.argv[1].lower()

    if mode == "extract":
        image_path = sys.argv[2]
        emb, bbox, det_conf = get_face_embedding_with_meta(image_path)
        if emb is not None:
            print(json.dumps({
                "status": "SUCCESS",
                "embedding": emb,
                "bbox": bbox,
                "det_confidence": det_conf
            }))
        else:
            print(json.dumps({"status": "NO_FACE_DETECTED"}))

    elif mode == "match":
        query_path = sys.argv[2]
        candidates_json_path = sys.argv[3]

        query_emb, bbox, det_conf = get_face_embedding_with_meta(query_path)
        if query_emb is None:
            print(json.dumps({"status": "NO_FACE_DETECTED"}))
            sys.exit(0)

        try:
            with open(candidates_json_path, 'r', encoding='utf-8') as f:
                candidates = json.load(f)
        except Exception as e:
            print(json.dumps({"status": "ERROR", "message": str(e)}))
            sys.exit(1)

        best_cand = None
        best_score = 0.0

        for cand in candidates:
            c_emb = cand.get('embedding')
            if not c_emb:
                continue
            score = compute_similarity(query_emb, c_emb)
            if score > best_score:
                best_score = score
                best_cand = cand

        THRESHOLD = 68.0  # Cutoff verification score (68.0%)

        if best_cand and best_score >= THRESHOLD:
            print(json.dumps({
                "status": "MATCH_FOUND",
                "name": best_cand.get("name", "Unknown Suspect"),
                "case_number": best_cand.get("case_number", "N/A"),
                "confidence": best_score,
                "bbox": bbox,
                "det_confidence": det_conf
            }))
        else:
            print(json.dumps({
                "status": "NO_MATCH",
                "top_confidence": best_score,
                "bbox": bbox,
                "det_confidence": det_conf
            }))
    else:
        print(json.dumps({"status": "ERROR", "message": f"Unknown mode: {mode}"}))
        sys.exit(1)