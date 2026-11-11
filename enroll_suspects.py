import os
import mysql.connector
from facenet_engine import get_face_embedding_with_meta

# 1. Database Connection Config
DB_CONFIG = {
    "host": "localhost",
    "user": "root",
    "password": "your_mysql_password",  # Replace with your MySQL password
    "database": "criminal_db"             # Replace with your database name
}

# 2. Define the 6 Suspect Photos and Details
SUSPECTS_DATA = [
    {
        "name": "Marcus Vance",
        "case_number": "CR-2026-0891",
        "image_file": "sample_suspect.jpeg"
    },
    {
        "name": "Elena Rostova",
        "case_number": "CR-2026-0412",
        "image_file": "suspect_2.jpg"
    },
    {
        "name": "David Chen",
        "case_number": "CR-2026-1104",
        "image_file": "suspect_3.jpg"
    },
    {
        "name": "Tariq Al-Mansoor",
        "case_number": "CR-2026-0733",
        "image_file": "suspect_4.jpg"
    },
    {
        "name": "Sophia Becker",
        "case_number": "CR-2026-0925",
        "image_file": "suspect_5.jpg"
    },
    {
        "name": "Vikram Patel",
        "case_number": "CR-2026-0318",
        "image_file": "suspect_6.jpg"
    }
]

def enroll():
    conn = mysql.connector.connect(**DB_CONFIG)
    cursor = conn.cursor()

    insert_sql = """
        INSERT INTO suspect_faces (suspect_name, case_number, image_path, embedding)
        VALUES (%s, %s, %s, %s)
        ON DUPLICATE KEY UPDATE
            case_number = VALUES(case_number),
            image_path = VALUES(image_path),
            embedding = VALUES(embedding);
    """

    for suspect in SUSPECTS_DATA:
        img_name = suspect["image_file"]
        img_path = os.path.abspath(img_name)

        if not os.path.isfile(img_path):
            print(f"[SKIP] File not found: {img_path}")
            continue

        print(f"Extracting 512-D embedding for: {suspect['name']} ({img_name})...")
        embedding, bbox, conf = get_face_embedding_with_meta(img_path)

        if embedding is None:
            print(f"[REJECTED] No face detected in {img_name}. Skipping enrollment.")
            continue

        # Convert float list to comma-separated string for MEDIUMTEXT storage
        embedding_str = ",".join(map(str, embedding))

        cursor.execute(insert_sql, (
            suspect["name"],
            suspect["case_number"],
            img_path,
            embedding_str
        ))
        print(f"[SUCCESS] Enrolled {suspect['name']} (Confidence: {conf}%, Box: {bbox})")

    conn.commit()
    cursor.close()
    conn.close()
    print("\nEnrollment complete! All 6 records updated in MySQL.")

if __name__ == "__main__":
    enroll()