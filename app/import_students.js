const admin = require("firebase-admin");
const fs = require("fs");
const csv = require("csv-parser");
const serviceAccount = require("./serviceAccountKey.json");

// Firebase Admin SDK Initialization
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();

// ── Database Configuration Constants ──
const SESSION_ID    = "8uZlPDYzF7YCFBi9BkX2";
const COURSE_ID     = "4hT4MunyYv0Ok2d7t5V7";
const YEAR_ID       = "Pe1040o4MEAaIhZ7VUOT";
const SEMESTER_ID   = "4sogZpnzRqVDgX9KC3rt";
const BATCH_ID      = "evMxLC6kJcayfGH3F7xN";

/**
 * Converts date string (DD-MM-YYYY) to Firestore Timestamp
 */
function parseDate(dateStr) {
  if (!dateStr) return null;
  const parts = dateStr.split("-");
  if (parts.length !== 3) return null;
  const day = parseInt(parts[0]);
  const month = parseInt(parts[1]) - 1; // Months are 0-based in JS
  const year = parseInt(parts[2]);
  return admin.firestore.Timestamp.fromDate(new Date(year, month, day));
}

/**
 * Main function to read CSV and upload data to Firestore
 */
async function importFromCsv() {
  console.log("Starting CSV Import to Firebase...\n");

  const results = [];

  // Read data from the CSV file
  fs.createReadStream("academy_hub_final.csv")
    .pipe(csv())
    .on("data", (data) => results.push(data))
    .on("end", async () => {
      let successCount = 0;
      let failureCount = 0;

      for (const row of results) {
        try {
          const studentData = {
            fullName:      row["Full Name"],
            mobileNumber:  row["Mobile Number"],
            emailAddress:  row["Email Address"],
            gender:        row["Gender"],
            dateOfBirth:   parseDate(row["Date of Birth"]),
            address:       row["Address"],
            rollNumber:    parseInt(row["Roll Number"]),
            admissionDate: parseDate(row["Admission Date"]),

            // Reference IDs
            sessionId:    SESSION_ID,
            courseId:     COURSE_ID,
            yearId:       YEAR_ID,
            semesterId:   SEMESTER_ID,
            batchId:      BATCH_ID,

            isActive:     true,
            createdAt:    admin.firestore.FieldValue.serverTimestamp()
          };

          // Uploading to Firestore "Students" collection
          await db.collection("Students").add(studentData);
          console.log(`Success: Added student ${row["Full Name"]}`);
          successCount++;
        } catch (error) {
          console.log(`Error: Failed to add ${row["Full Name"]} -> ${error.message}`);
          failureCount++;
        }
      }

      console.log(`\n🎉 Process Finished!`);
      console.log(`Total Success: ${successCount}`);
      console.log(`Total Failures: ${failureCount}`);
      process.exit(0);
    });
}

importFromCsv().catch(err => {
  console.error("Critical Script Error:", err);
  process.exit(1);
});