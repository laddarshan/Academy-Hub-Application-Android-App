const admin = require("firebase-admin");
const serviceAccount = require("./serviceAccountKey.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();

// ── Academic IDs (તમારા પ્રોજેક્ટ મુજબ) ──
const SESSION_ID    = "8uZlPDYzF7YCFBi9BkX2";
const SESSION_NAME  = "2025-2026";
const COURSE_ID     = "4hT4MunyYv0Ok2d7t5V7";
const COURSE_NAME   = "BCA";
const YEAR_ID       = "Pe1040o4MEAaIhZ7VUOT";
const YEAR_NAME     = "TY";
const SEMESTER_ID   = "4sogZpnzRqVDgX9KC3rt";
const SEMESTER_NAME = "Sem-6";
const SEMESTER_NUM  = 6;
const BATCH_ID      = "evMxLC6kJcayfGH3F7xN";
const BATCH_NAME    = "Div 1";

// ૧૦ વિદ્યાર્થીઓનો ડેટા
const students = [
  { name: "Lad Darshan Sumanbhai", roll: "1", mobile: "9825012345", email: "laddarshan25@gmail.com", dob: "25/05/2003", addr: "1348, Shivechha Society Thala, chikhli" },
  { name: "Patel Rajvi Shaileshbhai", roll: "2", mobile: "9712345678", email: "rajvi.patel@gmail.com", dob: "12/08/2004", addr: "Navsari" },
  { name: "Mistry Aryan Rajeshbhai", roll: "3", mobile: "9904561234", email: "aryan.mistry@gmail.com", dob: "05/01/2004", addr: "Bilimora" },
  { name: "Desai Hetvi Vinodbhai", roll: "4", mobile: "8866123456", email: "hetvi.desai@gmail.com", dob: "19/11/2003", addr: "Gandevi" },
  { name: "Gohil Fenil Bharatbhai", roll: "5", mobile: "7755661122", email: "fenil.gohil@gmail.com", dob: "22/03/2004", addr: "Chikhli" },
  { name: "Naik Diya Amitbhai", roll: "6", mobile: "9426112233", email: "diya.naik@gmail.com", dob: "14/02/2004", addr: "Navsari" },
  { name: "Rathod Meet Sureshbhai", roll: "7", mobile: "9012345678", email: "meet.rathod@gmail.com", dob: "30/09/2003", addr: "Bilimora" },
  { name: "Panchal Jiya Hareshbhai", roll: "8", mobile: "8123456789", email: "jiya.panchal@gmail.com", dob: "10/06/2004", addr: "Gandevi" },
  { name: "Shah Veer Nirajbhai", roll: "9", mobile: "7234567890", email: "veer.shah@gmail.com", dob: "28/12/2003", addr: "Chikhli" },
  { name: "Mehta Esha Alkeshbhai", roll: "10", mobile: "9632587410", email: "esha.mehta@gmail.com", dob: "01/04/2004", addr: "Navsari" }
];

async function importStudents() {
  console.log("🚀 Starting Import...");

  for (const s of students) {
    try {
      const docRef = db.collection("Students").doc(); // Auto ID generate

      const docData = {
        id:            docRef.id,
        name:          s.name,
        mobile:        s.mobile,        // Matches Student.kt
        email:         s.email,
        gender:        "Male",
        dateOfBirth:   s.dob,           // Matches Student.kt
        address:       s.addr,
        rollNumber:    s.roll,          // Matches Student.kt
        admissionDate: "15/06/2023",

        // IDs
        sessionId:    SESSION_ID,
        sessionName:  SESSION_NAME,
        courseId:     COURSE_ID,
        courseName:   COURSE_NAME,
        yearId:       YEAR_ID,
        yearName:     YEAR_NAME,
        yearNumber:   3,
        semesterId:   SEMESTER_ID,
        semesterName: SEMESTER_NAME,
        semesterNumber: SEMESTER_NUM,
        batchId:      BATCH_ID,
        batchName:    BATCH_NAME,

        status: "active",
        hasPhoto: false,
        profileImageBase64: "",
        attendancePercentage: 0.0,
        feesStatus: "pending",
        totalFees: 0.0,
        paidFees: 0.0,
        createdAt: Date.now(),
        updatedAt: Date.now()
      };

      await docRef.set(docData);
      console.log(`✅ Added: ${s.name}`);

    } catch (error) {
      console.log(`❌ Error: ${s.name} -> ${error.message}`);
    }
  }
  console.log("\n🎉 All Data Imported!");
  process.exit(0);
}

importStudents();