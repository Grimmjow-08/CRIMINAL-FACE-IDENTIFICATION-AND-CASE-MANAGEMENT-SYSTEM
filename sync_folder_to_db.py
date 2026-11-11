import os
import mysql.connector
from facenet_engine import get_face_embedding_with_meta

DB_CONFIG = {
    "host": "localhost",
    "user": "root",
    "password": os.environ["CRIMINAL_DB_PASSWORD"],
    "database": "criminal_db"
}

FOLDER_PATH = r"D:\APP Project\CriminalDatabaseManagement"

# Pre-defined list of suspect dossiers to map
SUSPECT_POOL = [
    ("Marcus Vance", "CR-2026-0891"),
    ("Elena Rostova", "CR-2026-0412"),
    ("David Chen", "CR-2026-1104"),
    ("Tariq Al-Mansoor", "CR-2026-0733"),
    ("Sophia Becker", "CR-2026-0925"),
    ("Vikram Patel", "CR-2026-0318")
]

def main():
    valid_exts = (".jpg", ".jpeg", ".png")

    # 1. Grab every single image physically sitting in your folder
    all_files = [
        f for f in os.listdir(FOLDER_PATH)
        if f.lower().endswith(valid_exts) and os.path.isfile(os.path.join(FOLDER_PATH, f))
    ]

    print(f"[*] Found {len(all_files)} images in {FOLDER_PATH}:")
    for f in all_files:
        print(f"    -> {f}")

    if not all_files:
        print("[!] No images found! Place your photos in the folder first.")
        return

    conn = mysql.connector.connect(**DB_CONFIG)
    cur = conn.cursor()

    # Clear old stale paths and register the real ones
    upsert_sql = """
        INSERT INTO suspect_faces (suspect_name, case_number, image_path, embedding)
        VALUES (%s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            case_number = VALUES(case_number),
            image_path = VALUES(image_path),
            embedding = VALUES(embedding);
    """

    enrolled = 0
    for idx, img_name in enumerate(all_files):
        if idx >= len(SUSPECT_POOL):
            break

        suspect_name, case_number = SUSPECT_POOL[idx]
        full_image_path = os.path.join(FOLDER_PATH, img_name).replace("\\", "/")

        print(f"\n[*] Processing [{suspect_name}] with file: {img_name}")
        emb, bbox, conf = get_face_embedding_with_meta(full_image_path)

        if emb is None:
            print(f"    [!] Skipping {img_name}: No facial geometry detected by MTCNN.")
            continue

        emb_str = ",".join(map(str, emb))
        cur.execute(upsert_sql, (suspect_name, case_number, full_image_path, emb_str))
        print(f"    [OK] Linked to DB -> Name: {suspect_name} | Path: {full_image_path}")
        enrolled += 1

    conn.commit()
    cur.close()
    conn.close()

    print(f"\n[DONE] Successfully registered {enrolled} suspect photos into MySQL!")

if __name__ == "__main__":
    main()