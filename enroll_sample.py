import os
import mysql.connector
from facenet_engine import get_face_embedding

# Use the exact .jpeg filename
SAMPLE_IMAGE = "sample_suspect.jpeg"
SUSPECT_NAME = "Marcus Vance"
CASE_NUM = "CR-2026-00001"

emb = get_face_embedding(SAMPLE_IMAGE)
if emb:
    emb_str = ",".join(map(str, emb))
    db = mysql.connector.connect(
        host="localhost",
        user="root",
        password=os.environ["CRIMINAL_DB_PASSWORD"],
        database="criminal_db"
    )
    cursor = db.cursor()
    cursor.execute(
        "INSERT INTO suspect_faces (suspect_name, case_number, image_path, embedding) VALUES (%s, %s, %s, %s)",
        (SUSPECT_NAME, CASE_NUM, SAMPLE_IMAGE, emb_str)
    )
    db.commit()
    print("Enrolled suspect successfully!")
else:
    print("No face detected in sample image.")