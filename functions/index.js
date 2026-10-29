// functions/index.js

const admin = require('firebase-admin');
const functions = require('firebase-functions');

admin.initializeApp({
    databaseURL: "https://academy-hub-57d8b-default-rtdb.firebaseio.com"
});

/**
 * sendTopicNotification
 */
exports.sendTopicNotification = functions.https.onCall(async (data, context) => {
    console.log('\n=== sendTopicNotification STARTED ===');
    console.log('Received data keys:', Object.keys(data || {}));

    // ✅ FIX: 2nd Gen Functions ma data nested hoi shake chhe — data.data ni andar
    const payload = data?.data || data;

    const topic   = payload?.topic;
    const title   = payload?.title;
    const body    = payload?.body;
    const type    = payload?.type;
    const idToken = payload?.idToken;

    console.log('  topic:',   `"${topic}"`);
    console.log('  title:',   `"${title}"`);
    console.log('  body:',    `"${body}"`);
    console.log('  type:',    `"${type}"`);
    console.log('  idToken length:', idToken?.length || 0);

    // ✅ STEP 1: Validate
    if (!topic) {
        console.error('❌ topic is missing');
        throw new functions.https.HttpsError('invalid-argument', 'Missing required parameter: topic');
    }
    if (!title) {
        console.error('❌ title is missing');
        throw new functions.https.HttpsError('invalid-argument', 'Missing required parameter: title');
    }
    if (!body) {
        console.error('❌ body is missing');
        throw new functions.https.HttpsError('invalid-argument', 'Missing required parameter: body');
    }
    if (!idToken) {
        console.error('❌ idToken is missing');
        throw new functions.https.HttpsError('invalid-argument', 'Missing required parameter: idToken');
    }

    console.log('✅ All parameters validated');

    // ✅ STEP 2: Verify ID token
    let decodedToken;
    try {
        decodedToken = await admin.auth().verifyIdToken(idToken);
        console.log('✅ Token verified for user:', decodedToken.uid);
    } catch (error) {
        console.error('❌ Token verification failed:', error.message);
        throw new functions.https.HttpsError(
            'unauthenticated',
            'Invalid or expired authentication token: ' + error.message
        );
    }

    const uid   = decodedToken.uid;
    const email = decodedToken.email || 'unknown';

    // ✅ STEP 3: Admin check — Realtime DB
    try {
        const userRef  = await admin.database().ref(`Users/${uid}`).once('value');
        const userData = userRef.val();

        console.log('User data found:', userData ? 'YES' : 'NO');

        if (!userData) {
            throw new functions.https.HttpsError('not-found', 'User profile not found.');
        }

        console.log('User role:', userData.role);

        if (userData.role !== 'admin') {
            throw new functions.https.HttpsError(
                'permission-denied',
                `User role is "${userData.role}", not "admin"`
            );
        }

        console.log('✅ User is admin');
    } catch (error) {
        if (error instanceof functions.https.HttpsError) throw error;
        console.error('❌ Admin check error:', error.message);
        throw new functions.https.HttpsError('internal', 'Error verifying permissions: ' + error.message);
    }

    // ✅ STEP 4: Send notification
    try {
        const response = await admin.messaging().send({
            notification: {
                title: title,
                body:  body
            },
            data: {
                type:        type || 'notification',
                timestamp:   new Date().toISOString(),
                sentBy:      uid,
                sentByEmail: email
            },
            topic: topic
        });

        console.log('✅ Notification sent! MessageId:', response);

        // Audit log
        try {
            await admin.firestore().collection('NotificationLogs').add({
                topic:       topic,
                title:       title,
                body:        body,
                type:        type,
                sentBy:      uid,
                sentByEmail: email,
                messageId:   response,
                timestamp:   admin.firestore.FieldValue.serverTimestamp(),
                status:      'sent'
            });
            console.log('✅ Logged to NotificationLogs');
        } catch (logError) {
            console.warn('⚠️ Log failed (non-critical):', logError.message);
        }

        return {
            success:   true,
            messageId: response,
            topic:     topic,
            message:   'Notification sent successfully'
        };

    } catch (error) {
        if (error instanceof functions.https.HttpsError) throw error;
        console.error('❌ Send failed:', error.message);
        throw new functions.https.HttpsError('internal', 'Failed to send notification: ' + error.message);
    }
});


// ════════════════════════════════════════════════════════════════
// sendNotificationToUsers
// ════════════════════════════════════════════════════════════════

exports.sendNotificationToUsers = functions.https.onCall(async (data, context) => {
    console.log('=== sendNotificationToUsers called ===');

    // ✅ FIX: 2nd Gen nested data fix
    const payload = data?.data || data;
    const { userIds, title, body, idToken } = payload;

    // Verify ID token
    let decodedToken;
    try {
        decodedToken = await admin.auth().verifyIdToken(idToken);
    } catch (error) {
        throw new functions.https.HttpsError('unauthenticated', 'Invalid or expired token');
    }

    const uid = decodedToken.uid;

    // Admin check — Realtime DB
    try {
        const userRef  = await admin.database().ref(`Users/${uid}`).once('value');
        const userData = userRef.val();

        if (!userData || userData.role !== 'admin') {
            throw new functions.https.HttpsError('permission-denied', 'Only admins can send notifications');
        }
    } catch (error) {
        if (error instanceof functions.https.HttpsError) throw error;
        throw new functions.https.HttpsError('permission-denied', 'User is not authorized');
    }

    if (!Array.isArray(userIds) || userIds.length === 0) {
        throw new functions.https.HttpsError('invalid-argument', 'userIds must be a non-empty array');
    }

    try {
        const fcmTokens = [];

        for (const userId of userIds) {
            const tokenRef = await admin.database().ref(`Users/${userId}/fcmToken`).once('value');
            const token    = tokenRef.val();
            if (token) fcmTokens.push(token);
        }

        if (fcmTokens.length === 0) {
            throw new functions.https.HttpsError('not-found', 'No valid FCM tokens found');
        }

        const results = await admin.messaging().sendEachForMulticast({
            tokens: fcmTokens,
            notification: {
                title: title,
                body:  body
            },
            data: {
                timestamp: new Date().toISOString()
            }
        });

        console.log(`Sent: ${results.successCount}, Failed: ${results.failureCount}`);

        return {
            success:      true,
            successCount: results.successCount,
            failureCount: results.failureCount
        };

    } catch (error) {
        if (error instanceof functions.https.HttpsError) throw error;
        console.error('Error:', error);
        throw new functions.https.HttpsError('internal', 'Failed to send: ' + error.message);
    }
});


// ════════════════════════════════════════════════════════════════
// uploadInternalPdf
// ════════════════════════════════════════════════════════════════

exports.uploadInternalPdf = functions.https.onCall(async (data, context) => {
    console.log('=== uploadInternalPdf called ===');

    // ✅ FIX: 2nd Gen nested data fix
    const payload = data?.data || data;
    const { fileName, pdfData, semesterId, sessionId } = payload;

    if (!fileName || !pdfData || !semesterId || !sessionId) {
        throw new functions.https.HttpsError('invalid-argument', 'Missing required parameters');
    }

    try {
        const buffer   = Buffer.from(pdfData, 'base64');
        const bucket   = admin.storage().bucket();
        const filePath = `results/${semesterId}/${sessionId}/${fileName}`;

        const file = bucket.file(filePath);
        await file.save(buffer, {
            metadata: {
                contentType: 'application/pdf',
                metadata: {
                    uploadedAt: new Date().toISOString()
                }
            }
        });

        console.log(`✅ PDF uploaded: ${filePath}`);

        const docId = `${semesterId}_${sessionId}`;
        await admin.firestore().collection('Results').doc(docId).set({
            internalUploaded:   true,
            internalFileName:   fileName,
            internalFilePath:   filePath,
            internalUploadedAt: admin.firestore.FieldValue.serverTimestamp(),
            semesterId:         semesterId,
            sessionId:          sessionId
        }, { merge: true });

        return {
            success:  true,
            filePath: filePath,
            message:  'PDF uploaded successfully'
        };

    } catch (error) {
        console.error(' PDF upload error:', error);
        throw new functions.https.HttpsError('internal', 'Failed to upload PDF: ' + error.message);
    }
});