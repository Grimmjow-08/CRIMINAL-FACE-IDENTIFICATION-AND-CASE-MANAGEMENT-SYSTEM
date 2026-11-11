import os
import mysql.connector
from facenet_engine import get_face_embedding

# Configure your 5 suspects here
SUSPECTS_DATA = [
    {"name": "Marcus Vance", "case": "CR-2026-00001", "file": "suspect1.jpeg"},
    {"name": "Elena Rostova", "case": "CR-2026-00002", "file": "suspect2.jpeg"},
    {"name": "Damon Hayes", "case": "CR-2026-00003", "file": "suspect3.jpeg"},
    {"name": "Tariq Al-Mansoor", "case": "CR-2026-00004", "file": "suspect4.jpeg"},
    {"name": "Victor Chen", "case": "CR-2026-00005", "file": "suspect5.jpeg"},
    {"name": "Victor Chen", "case": "CR-2026-00006", "file": "suspect6.jpeg"}
]

db = mysql.connector.connect(
    host="localhost",
    user="root",
    password=os.environ["CRIMINAL_DB_PASSWORD"],
    database="criminal_db"
)
cursor = db.cursor()

print(">> Starting Batch Biometric Ingestion (FaceNet 512-D)...")

for suspect in SUSPECTS_DATA:
    filepath = suspect["file"]
    if not os.path.exists(filepath):
        print(f"[-] File missing: {filepath}, skipping.")
        continue

    emb = get_face_embedding(filepath)
    if emb is None:
        print(f"[-] No face detected in {filepath}, skipping.")
        continue

    emb_str = ",".join(map(str, emb))
    cursor.execute(
        "INSERT INTO suspect_faces (suspect_name, case_number, image_path, embedding) VALUES (%s, %s, %s, %s)",
        (suspect["name"], suspect["case"], filepath, emb_str)
    )
    db.commit()
    print(f"[+] Enrolled {suspect['name']} ({suspect['case']}) successfully!")

print(">> All valid faces enrolled into MySQL suspect_faces.")