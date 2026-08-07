/**
 * firebase_functions/index.js
 * Firebase Cloud Function that sends an FCM push notification to the callee
 * whenever a new call document is created in Firestore (/calls/{callId}).
 *
 * SETUP:
 *   npm install -g firebase-tools
 *   firebase login
 *   firebase init functions   (choose "Use existing project", select your project)
 *   Copy this file into functions/index.js
 *   firebase deploy --only functions
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const firestore = admin.firestore();

/**
 * Triggered when a new document appears in /calls.
 * Reads the callee's FCM token from Firestore and sends a high-priority
 * data message that wakes the device and launches IncomingCallActivity.
 */
exports.onCallCreated = functions.firestore
  .document("calls/{callId}")
  .onCreate(async (snap, context) => {
    const callData = snap.data();
    const callId = context.params.callId;

    const calleeDigitId = callData.calleeId;   // 4-digit ID of the callee
    const callerName    = callData.callerName;  // Display name of the caller
    const callerUid     = callData.callerId;    // Firebase Auth UID of the caller

    if (!calleeDigitId || !callerName) {
      console.error("Missing calleeId or callerName in call document", callId);
      return null;
    }

    try {
      // Step 1: Resolve callee uid from the digitIds index
      const digitIdDoc = await firestore
        .collection("digitIds")
        .doc(calleeDigitId)
        .get();

      if (!digitIdDoc.exists) {
        console.warn(`No digitId doc found for ${calleeDigitId}`);
        return null;
      }

      const calleeUid = digitIdDoc.data().uid;
      if (!calleeUid || calleeUid === "pending") {
        console.warn(`digitId ${calleeDigitId} has no valid uid`);
        return null;
      }

      // Step 2: Fetch the callee's user document to get the FCM token
      const userDoc = await firestore
        .collection("users")
        .doc(calleeUid)
        .get();

      if (!userDoc.exists) {
        console.warn(`User doc not found for uid ${calleeUid}`);
        return null;
      }

      const fcmToken = userDoc.data().fcmToken;
      if (!fcmToken) {
        console.warn(`No FCM token for user ${calleeUid}`);
        return null;
      }

      // Step 3: Get the caller's digit ID to include in the notification
      const callerUserDoc = await firestore
        .collection("users")
        .doc(callerUid)
        .get();
      const callerDigitId = callerUserDoc.exists
        ? callerUserDoc.data().fourDigitId
        : "????";

      // Step 4: Send the FCM data message (NOT a notification message,
      // so the app gets full control over the UI even when backgrounded)
      await admin.messaging().send({
        token: fcmToken,
        data: {
          type:           "incoming_call",
          callId:         callId,
          callerName:     callerName,
          callerDigitId:  callerDigitId,
        },
        android: {
          priority: "high",   // Wakes up the device even in Doze mode
          ttl: 30000,         // 30 seconds – drop the notification if the call is already gone
        },
      });

      console.log(`✅ Sent incoming-call notification to ${calleeUid} for call ${callId}`);
      return null;

    } catch (err) {
      console.error("Error sending incoming call FCM:", err);
      return null;
    }
  });

/**
 * (Optional) Clean up ended/rejected call documents after 1 hour
 * to keep Firestore tidy. Run as a scheduled function or on-update trigger.
 */
exports.onCallEnded = functions.firestore
  .document("calls/{callId}")
  .onUpdate(async (change, context) => {
    const newData = change.after.data();
    const endedStatuses = ["ended", "rejected", "missed"];

    if (endedStatuses.includes(newData.callStatus)) {
      // Schedule deletion after 1 hour (use a Cloud Tasks queue in production)
      // For simplicity, delete immediately:
      await change.after.ref.delete();
      console.log(`Deleted ended call document ${context.params.callId}`);
    }
    return null;
  });
