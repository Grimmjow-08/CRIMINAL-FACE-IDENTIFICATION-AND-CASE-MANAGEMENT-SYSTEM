# Criminal Face Identification and Case Management System

A prototype desktop case-management application built with Java Swing, MySQL, and a Python FaceNet pipeline. Face detection uses MTCNN; face embeddings and similarity matching use `InceptionResnetV1` pretrained on VGGFace2.

## Requirements

- JDK 17 or newer
- Maven
- MySQL with the application schema created
- Python available as `python` on `PATH`

The repository does not include database schema or migration scripts. The application and enrollment scripts expect a `criminal_db` database; face enrollment and matching use a `suspect_faces` table with `suspect_name`, `case_number`, `image_path`, and `embedding` columns.

## Configure the database

Copy the example configuration and set the local MySQL credentials:

```powershell
Copy-Item src/main/resources/db.properties.example src/main/resources/db.properties
```

Edit `src/main/resources/db.properties`. This local file is ignored by Git.

## Install Python dependencies

From the project directory, install the FaceNet and database packages:

```powershell
python -m pip install facenet-pytorch opencv-python mysql-connector-python
```

The first FaceNet run may download pretrained model weights. The engine uses CUDA when available and otherwise runs on CPU. The Java application starts the engine by running `python facenet_engine.py`, so Python must be available on `PATH` when launching the app.

Enrollment scripts also read the database password from `CRIMINAL_DB_PASSWORD`. In PowerShell, set it for the current session before running a script:

```powershell
$env:CRIMINAL_DB_PASSWORD = "your-local-mysql-password"
python enroll_suspects.py
```

## Build and run

From the project root:

```powershell
mvn clean package
java -jar target/CriminalDatabaseManagement-1.0-SNAPSHOT.jar
```

## Face engine commands

Extract an embedding and detection metadata from an image:

```powershell
python facenet_engine.py extract path/to/image.jpg
```

Compare a query image against a JSON array of candidates containing `name`, `case_number`, and 512-dimensional `embedding` fields:

```powershell
python facenet_engine.py match path/to/query.jpg path/to/candidates.json
```

## Prototype notice

This project is a prototype. Do not use its face-match results as the sole basis for identifying a person or making investigative, legal, or other consequential decisions. Use only synthetic or otherwise authorized data, and protect biometric and case information appropriately.