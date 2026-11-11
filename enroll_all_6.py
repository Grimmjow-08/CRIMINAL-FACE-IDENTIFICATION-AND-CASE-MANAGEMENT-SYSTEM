import os
import mysql.connector
from facenet_engine import get_face_embedding_with_meta

DB_CONFIG = {
    "host": "localhost",
    "user": "root",
    "password": os.environ["CRIMINAL_DB_PASSWORD"],
    "database": "criminal_db"
}

# Physical folder holding the actual mugshots
USER_PROFILE = os.environ.get("USERPROFILE", "C:/Users/Nitin")
MUGSHOTS_DIR = os.path.join(USER_PROFILE, "Documents", "Mug Shots").replace("\\", "/")

# Suspect metadata matching MugShort-1 through MugShort-6
SUSPECTS = [
    ("Marcus Vance",     "CR-2026-0891", 1),
    ("Elena Rostova",    "CR-2026-0412", 2),
    ("David Chen",       "CR-2026-1104", 3),
    ("Tariq Al-Mansoor", "CR-2026-0733", 4),
    ("Sophia Becker",    "CR-2026-0925", 5),
    ("Vikram Patel",     "CR-2026-0318", 6),
]

def find_file(folder, idx):
    names = [f"MugShort-{idx}", f"MugShot-{idx}", f"mugshort-{idx}", f"mugshot-{idx}"]
    exts = [".jpeg", ".jpg", ".png", ".JPEG", ".JPG", ".PNG"]
    for n in names:
        for ext in exts:
            p = os.path.join(folder, n + ext)
            if os.path.exists(p):
                return p.replace("\\", "/")
    return None

def main():
    print(f"[*] Reading Mugshots from: {MUGSHOTS_DIR}")
    if not os.path.exists(MUGSHOTS_DIR):
        print(f"[!] Directory not found: {MUGSHOTS_DIR}")
        return

    conn = mysql.connector.connect(**DB_CONFIG)
    cur = conn.cursor()

    # Clear old entries that had the Faces folder paths
    cur.execute("TRUNCATE TABLE suspect_faces;")
    conn.commit()
    print("[*] Cleared old entries from suspect_faces table.")

    insert_sql = """
        INSERT INTO suspect_faces (suspect_name, case_number, image_path, embedding)
        VALUES (%s, %s, %s, %s);
    """

    for name, case_no, idx in SUSPECTS:
        img_path = find_file(MUGSHOTS_DIR, idx)
        if not img_path:
            print(f"[!] Missing file for index {idx} in {MUGSHOTS_DIR}")
            continue

        print(f"[*] Processing Mugshot: {os.path.basename(img_path)} for {name}...")
        emb, bbox, conf = get_face_embedding_with_meta(img_path)

        if emb is None:
            print(f"    [!] No face detected in {img_path}")
            continue

        emb_str = ",".join(map(str, emb))
        cur.execute(insert_sql, (name, case_no, img_path, emb_str))
        print(f"    [OK] Stored Mugshot vector for {name} ({case_no}) -> {img_path}")

    conn.commit()
    cur.close()
    conn.close()
    print("\n[SUCCESS] Database populated with official Mug Shots!")

if __name__ == "__main__":
    main()