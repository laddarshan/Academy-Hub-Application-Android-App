const admin = require("firebase-admin");
const fs = require("fs");

const serviceAccount = require("./serviceAccountKey.json");
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

// ✅ Fixed IDs
const SESSION_ID    = "djHdKr907UuNQTlIMQeN";
const SESSION_NAME  = "2025-2026";
const COURSE_ID     = "DLSGPjryOrJ2ckTDPbXb";
const COURSE_NAME   = "BCA";
const YEAR_ID       = "p0ZunCzERX3QAULAuJ2R";
const YEAR_NAME     = "TY";
const SEMESTER_ID   = "rnRtQRTB52xOEHBJAARt";
const SEMESTER_NAME = "Sem-6";
const SEMESTER_NUM  = 6;
const BATCH_ID      = "jhQL4aCDiJ8H8rtSy7Zd";
const BATCH_NAME    = "Div 1";

// ✅ 50 Students data
const students = [
  { name: "Aarav Patel",     mobile: "9879541001", email: "aarav.patel@gmail.com",     gender: "Male",   dateOfBirth: "12/03/2004", address: "Surat",       rollNumber: "01" },
  { name: "Priya Shah",      mobile: "9879541002", email: "priya.shah@gmail.com",      gender: "Female", dateOfBirth: "05/07/2004", address: "Bilimora",    rollNumber: "02" },
  { name: "Ravi Desai",      mobile: "9879541003", email: "ravi.desai@gmail.com",      gender: "Male",   dateOfBirth: "18/11/2003", address: "Navsari",     rollNumber: "03" },
  { name: "Nisha Trivedi",   mobile: "9879541004", email: "nisha.trivedi@gmail.com",   gender: "Female", dateOfBirth: "22/09/2004", address: "Valsad",      rollNumber: "04" },
  { name: "Karan Mehta",     mobile: "9879541005", email: "karan.mehta@gmail.com",     gender: "Male",   dateOfBirth: "30/01/2004", address: "Chikhli",     rollNumber: "05" },
  { name: "Pooja Joshi",     mobile: "9879541006", email: "pooja.joshi@gmail.com",     gender: "Female", dateOfBirth: "14/06/2004", address: "Surat",       rollNumber: "06" },
  { name: "Vivek Sharma",    mobile: "9879541007", email: "vivek.sharma@gmail.com",    gender: "Male",   dateOfBirth: "08/04/2003", address: "Bharuch",     rollNumber: "07" },
  { name: "Sneha Rao",       mobile: "9879541008", email: "sneha.rao@gmail.com",       gender: "Female", dateOfBirth: "25/12/2003", address: "Ankleshwar",  rollNumber: "08" },
  { name: "Nikhil Gupta",    mobile: "9879541009", email: "nikhil.gupta@gmail.com",    gender: "Male",   dateOfBirth: "17/08/2004", address: "Bardoli",     rollNumber: "09" },
  { name: "Komal Panchal",   mobile: "9879541010", email: "komal.panchal@gmail.com",   gender: "Female", dateOfBirth: "03/02/2004", address: "Navsari",     rollNumber: "10" },
  { name: "Arjun Modi",      mobile: "9879541011", email: "arjun.modi@gmail.com",      gender: "Male",   dateOfBirth: "11/05/2004", address: "Surat",       rollNumber: "11" },
  { name: "Dipti Patel",     mobile: "9879541012", email: "dipti.patel@gmail.com",     gender: "Female", dateOfBirth: "29/10/2003", address: "Bilimora",    rollNumber: "12" },
  { name: "Mihir Shah",      mobile: "9879541013", email: "mihir.shah@gmail.com",      gender: "Male",   dateOfBirth: "07/03/2004", address: "Valsad",      rollNumber: "13" },
  { name: "Kajal Desai",     mobile: "9879541014", email: "kajal.desai@gmail.com",     gender: "Female", dateOfBirth: "16/07/2004", address: "Chikhli",     rollNumber: "14" },
  { name: "Yash Trivedi",    mobile: "9879541015", email: "yash.trivedi@gmail.com",    gender: "Male",   dateOfBirth: "24/09/2003", address: "Navsari",     rollNumber: "15" },
  { name: "Ritu Mehta",      mobile: "9879541016", email: "ritu.mehta@gmail.com",      gender: "Female", dateOfBirth: "02/01/2004", address: "Surat",       rollNumber: "16" },
  { name: "Pratik Joshi",    mobile: "9879541017", email: "pratik.joshi@gmail.com",    gender: "Male",   dateOfBirth: "19/06/2004", address: "Bharuch",     rollNumber: "17" },
  { name: "Mansi Sharma",    mobile: "9879541018", email: "mansi.sharma@gmail.com",    gender: "Female", dateOfBirth: "28/04/2003", address: "Ankleshwar",  rollNumber: "18" },
  { name: "Rohan Gupta",     mobile: "9879541019", email: "rohan.gupta@gmail.com",     gender: "Male",   dateOfBirth: "13/08/2004", address: "Bardoli",     rollNumber: "19" },
  { name: "Hetal Panchal",   mobile: "9879541020", email: "hetal.panchal@gmail.com",   gender: "Female", dateOfBirth: "06/02/2004", address: "Navsari",     rollNumber: "20" },
  { name: "Sagar Modi",      mobile: "9879541021", email: "sagar.modi@gmail.com",      gender: "Male",   dateOfBirth: "21/05/2004", address: "Surat",       rollNumber: "21" },
  { name: "Riddhi Patel",    mobile: "9879541022", email: "riddhi.patel@gmail.com",    gender: "Female", dateOfBirth: "10/11/2003", address: "Bilimora",    rollNumber: "22" },
  { name: "Deven Shah",      mobile: "9879541023", email: "deven.shah@gmail.com",      gender: "Male",   dateOfBirth: "04/03/2004", address: "Valsad",      rollNumber: "23" },
  { name: "Foram Desai",     mobile: "9879541024", email: "foram.desai@gmail.com",     gender: "Female", dateOfBirth: "23/07/2004", address: "Chikhli",     rollNumber: "24" },
  { name: "Akash Trivedi",   mobile: "9879541025", email: "akash.trivedi@gmail.com",   gender: "Male",   dateOfBirth: "01/10/2003", address: "Navsari",     rollNumber: "25" },
  { name: "Shruti Mehta",    mobile: "9879541026", email: "shruti.mehta@gmail.com",    gender: "Female", dateOfBirth: "09/01/2004", address: "Surat",       rollNumber: "26" },
  { name: "Neel Joshi",      mobile: "9879541027", email: "neel.joshi@gmail.com",      gender: "Male",   dateOfBirth: "26/06/2004", address: "Bharuch",     rollNumber: "27" },
  { name: "Bhoomi Sharma",   mobile: "9879541028", email: "bhoomi.sharma@gmail.com",   gender: "Female", dateOfBirth: "15/04/2003", address: "Ankleshwar",  rollNumber: "28" },
  { name: "Parth Gupta",     mobile: "9879541029", email: "parth.gupta@gmail.com",     gender: "Male",   dateOfBirth: "20/08/2004", address: "Bardoli",     rollNumber: "29" },
  { name: "Janki Panchal",   mobile: "9879541030", email: "janki.panchal@gmail.com",   gender: "Female", dateOfBirth: "01/02/2004", address: "Navsari",     rollNumber: "30" },
  { name: "Harsh Modi",      mobile: "9879541031", email: "harsh.modi@gmail.com",      gender: "Male",   dateOfBirth: "27/05/2004", address: "Surat",       rollNumber: "31" },
  { name: "Drashti Patel",   mobile: "9879541032", email: "drashti.patel@gmail.com",   gender: "Female", dateOfBirth: "08/11/2003", address: "Bilimora",    rollNumber: "32" },
  { name: "Kunj Shah",       mobile: "9879541033", email: "kunj.shah@gmail.com",       gender: "Male",   dateOfBirth: "03/03/2004", address: "Valsad",      rollNumber: "33" },
  { name: "Minal Desai",     mobile: "9879541034", email: "minal.desai@gmail.com",     gender: "Female", dateOfBirth: "22/07/2004", address: "Chikhli",     rollNumber: "34" },
  { name: "Raj Trivedi",     mobile: "9879541035", email: "raj.trivedi@gmail.com",     gender: "Male",   dateOfBirth: "30/09/2003", address: "Navsari",     rollNumber: "35" },
  { name: "Urvi Mehta",      mobile: "9879541036", email: "urvi.mehta@gmail.com",      gender: "Female", dateOfBirth: "07/01/2004", address: "Surat",       rollNumber: "36" },
  { name: "Jay Joshi",       mobile: "9879541037", email: "jay.joshi@gmail.com",       gender: "Male",   dateOfBirth: "24/06/2004", address: "Bharuch",     rollNumber: "37" },
  { name: "Khushi Sharma",   mobile: "9879541038", email: "khushi.sharma@gmail.com",   gender: "Female", dateOfBirth: "13/04/2003", address: "Ankleshwar",  rollNumber: "38" },
  { name: "Meet Gupta",      mobile: "9879541039", email: "meet.gupta@gmail.com",      gender: "Male",   dateOfBirth: "18/08/2004", address: "Bardoli",     rollNumber: "39" },
  { name: "Zeel Panchal",    mobile: "9879541040", email: "zeel.panchal@gmail.com",    gender: "Female", dateOfBirth: "05/02/2004", address: "Navsari",     rollNumber: "40" },
  { name: "Dev Modi",        mobile: "9879541041", email: "dev.modi@gmail.com",        gender: "Male",   dateOfBirth: "23/05/2004", address: "Surat",       rollNumber: "41" },
  { name: "Vrutti Patel",    mobile: "9879541042", email: "vrutti.patel@gmail.com",    gender: "Female", dateOfBirth: "14/11/2003", address: "Bilimora",    rollNumber: "42" },
  { name: "Smit Shah",       mobile: "9879541043", email: "smit.shah@gmail.com",       gender: "Male",   dateOfBirth: "09/03/2004", address: "Valsad",      rollNumber: "43" },
  { name: "Heena Desai",     mobile: "9879541044", email: "heena.desai@gmail.com",     gender: "Female", dateOfBirth: "28/07/2004", address: "Chikhli",     rollNumber: "44" },
  { name: "Dhruv Trivedi",   mobile: "9879541045", email: "dhruv.trivedi@gmail.com",   gender: "Male",   dateOfBirth: "06/10/2003", address: "Navsari",     rollNumber: "45" },
  { name: "Payal Mehta",     mobile: "9879541046", email: "payal.mehta@gmail.com",     gender: "Female", dateOfBirth: "15/01/2004", address: "Surat",       rollNumber: "46" },
  { name: "Rushabh Joshi",   mobile: "9879541047", email: "rushabh.joshi@gmail.com",   gender: "Male",   dateOfBirth: "02/07/2004", address: "Bharuch",     rollNumber: "47" },
  { name: "Shital Sharma",   mobile: "9879541048", email: "shital.sharma@gmail.com",   gender: "Female", dateOfBirth: "19/04/2003", address: "Ankleshwar",  rollNumber: "48" },
  { name: "Fenil Gupta",     mobile: "9879541049", email: "fenil.gupta@gmail.com",     gender: "Male",   dateOfBirth: "25/08/2004", address: "Bardoli",     rollNumber: "49" },
  { name: "Trisha Panchal",  mobile: "9879541050", email: "trisha.panchal@gmail.com",  gender: "Female", dateOfBirth: "12/02/2004", address: "Navsari",     rollNumber: "50" },
];

async function uploadStudents() {
  console.log(`\n🚀 50 students upload karva shuru...`);
  let success = 0;

  for (let i = 0; i < students.length; i++) {
    const s = students[i];
    const docRef = db.collection("Students").doc();
    await docRef.set({
      id:                   docRef.id,
      name:                 s.name,
      mobile:               s.mobile,
      email:                s.email,
      gender:               s.gender,
      dateOfBirth:          s.dateOfBirth,
      address:              s.address,
      rollNumber:           s.rollNumber,
      profileImage:         "",
      sessionId:            SESSION_ID,
      sessionName:          SESSION_NAME,
      courseId:             COURSE_ID,
      courseName:           COURSE_NAME,
      yearId:               YEAR_ID,
      yearName:             YEAR_NAME,
      semesterId:           SEMESTER_ID,
      semesterName:         SEMESTER_NAME,
      semesterNumber:       SEMESTER_NUM,
      batchId:              BATCH_ID,
      batchName:            BATCH_NAME,
      status:               "active",
      feesStatus:           "pending",
      totalFees:            0,
      paidFees:             0,
      attendancePercentage: 0,
      admissionDate:        "01/06/2024",
      createdAt:            Date.now(),
      updatedAt:            Date.now(),
    });
    success++;
    console.log(`✅ [${i+1}/50] ${s.name} added!`);
  }

  console.log(`\n🎉 Done! ${success} students added successfully!`);
  process.exit(0);
}

uploadStudents();
