# Criminal Face Identification and Case Management System

A prototype desktop case-management application built with Java Swing, MySQL, and a Python FaceNet pipeline. Face detection uses MTCNN; face embeddings and similarity matching use `InceptionResnetV1` pretrained on VGGFace2.

## 1. Requirements

- JDK 17 or newer and Maven
- MySQL 8.x and its command-line client
- Python available as `python` on `PATH`

## 2. Database Setup

Create the database and tables from the project root:

```powershell
mysql -u root -p < sql/schema.sql
```

The script creates the `criminal_db` database and tables used by the current application: users, cases, victims, evidence, suspect face embeddings, and activity logs. It does not create user accounts or sample records. Create and configure an administrator account using your local setup process.

Copy the example Java configuration and set your local MySQL credentials:

```powershell
Copy-Item src/main/resources/db.properties.example src/main/resources/db.properties
```

Edit `src/main/resources/db.properties`. This local file is ignored by Git.

## 3. Build and Run

From the project root:

```powershell
mvn clean package
java -jar target/CriminalDatabaseManagement-1.0-SNAPSHOT.jar
```

The Java application starts the FaceNet engine by running `python facenet_engine.py`, so Python must be available on `PATH` when launching the app.

## 4. Face Engine

Install the Python packages listed in section 5. The first run may download pretrained model weights. The engine uses CUDA when available and otherwise runs on CPU.

Extract an embedding and detection metadata from an image:

```powershell
python facenet_engine.py extract path/to/image.jpg
```

Compare a query image against a JSON array of candidates containing `name`, `case_number`, and 512-dimensional `embedding` fields:

```powershell
python facenet_engine.py match path/to/query.jpg path/to/candidates.json
```

Enrollment scripts read the database password from `CRIMINAL_DB_PASSWORD`. Set it in PowerShell before running an enrollment script:

```powershell
$env:CRIMINAL_DB_PASSWORD = "your-local-mysql-password"
python enroll_suspects.py
```

## 5. Required Libraries and Dependencies

**Java**

- Java 17+
- MySQL Connector/J 8.3.0
- FlatLaf 3.4.1
- Maven Compiler Plugin 3.11.0 and Maven Shade Plugin 3.5.2

Maven resolves the Java libraries from `pom.xml`.

**Python**

- PyTorch
- `facenet-pytorch`
- NumPy
- OpenCV (`opencv-python`)
- Pillow
- MySQL Connector/Python (`mysql-connector-python`)

Install the Python packages with:

```powershell
python -m pip install facenet-pytorch opencv-python mysql-connector-python
```

## 6. Implemented Modules and Functionalities

- **Authentication and access requests:** administrator and investigator login, investigator registration with ID-proof path, and application-status lookup.
- **Administrator dashboard:** case and evidence summaries, investigator workload, notifications, and reporting metrics.
- **Case management:** create and edit case dossiers, record multiple victims, search/filter cases, assign investigators, and route cases through pending, active, rejected, and closed states.
- **Investigator management:** review pending investigator applications and manage investigator status.
- **Case requests:** review investigator case-intake and case-claim requests; approve, dispatch, or reject requests.
- **Evidence management:** submit evidence files, review and verify submissions, flag discrepancies, track revisions, and handle resubmissions.
- **Face identification:** detect faces, generate normalized 512-dimensional embeddings, compare against enrolled suspect embeddings, and return a match result through the investigator dashboard.
- **Reports and audit logs:** case/evidence/investigator metrics and searchable activity records.
- **Notifications and UI utilities:** administrator and investigator notifications, investigator loading splash, shared visual theme, and asynchronous audit logging.

## Prototype Notice

This project is a prototype. Do not use its face-match results as the sole basis for identifying a person or making investigative, legal, or other consequential decisions. Use only synthetic or otherwise authorized data, and protect biometric and case information appropriately
