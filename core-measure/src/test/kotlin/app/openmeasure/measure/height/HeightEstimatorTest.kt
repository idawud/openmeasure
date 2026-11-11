package app.openmeasure.measure.height

import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Plane
import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.height.SyntheticPerson.Companion.FLOOR
import app.openmeasure.measure.height.SyntheticPerson.Companion.portraitPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HeightEstimatorTest {
    private val estimator = HeightEstimator()

    private fun assertOk(r: HeightResult, expected: Float, tol: Float) {
        assertEquals("reason", HeightReason.OK, r.reason)
        assertNotNull(r.heightMeters)
        assertEquals(expected, r.heightMeters!!, tol)
    }

    @Test fun levelPortraitCamera_recoversHeight() {
        val person = SyntheticPerson(1.75f, personZ = -2.5f)
        val r = estimator.estimate(person.frame(portraitPose(1.2f)))
        assertOk(r, 1.75f, 0.01f)
        assertEquals(FloorSource.PLANE, r.floorSource)
        assertEquals(ConfidenceLevel.HIGH, r.confidenceLevel)
        assertEquals(2.5f, r.distanceM!!, 0.05f)
    }

    @Test fun tiltedCamera_recoversHeight() {
        for (h in listOf(1.55f, 1.75f, 1.92f)) {
            val person = SyntheticPerson(h, personZ = -3f)
            assertOk(estimator.estimate(person.frame(portraitPose(1.5f, pitchDownDeg = 15f))), h, 0.01f)
        }
    }

    @Test fun childAtTwoMetres_recoversHeight() {
        val person = SyntheticPerson(1.10f, personZ = -2f)
        assertOk(estimator.estimate(person.frame(portraitPose(1.0f, pitchDownDeg = 10f))), 1.10f, 0.01f)
    }

    @Test fun tablePlaneIsIgnored_floorChosenByFootDepth() {
        val person = SyntheticPerson(1.75f, personZ = -2.5f)
        val table = Plane(Vec3(0.5f, 0.75f, -1.5f), Vec3(0f, 1f, 0f))
        val r = estimator.estimate(person.frame(portraitPose(1.2f), planes = listOf(table, FLOOR)))
        assertOk(r, 1.75f, 0.01f)
    }

    @Test fun noPlane_fallsBackToFootDepth_cappedMedium() {
        val person = SyntheticPerson(1.75f, personZ = -2.5f)
        val r = estimator.estimate(person.frame(portraitPose(1.2f), planes = emptyList()))
        assertOk(r, 1.75f, 0.03f)
        assertEquals(FloorSource.DEPTH, r.floorSource)
        assertEquals(ConfidenceLevel.MEDIUM, r.confidenceLevel)
    }

    @Test fun noPlaneAndNoFootDepth_noFloor() {
        val person = SyntheticPerson(1.75f, personZ = -2.5f)
        val f = person.frame(portraitPose(1.2f), planes = emptyList())
        val headOnly = DepthLookup { u, v -> f.depth!!.pointAt(u, v)?.takeIf { it.position.y > 0.5f } }
        assertEquals(HeightReason.NO_FLOOR, estimator.estimate(f.copy(depth = headOnly)).reason)
    }

    @Test fun landscapeTooLowFraming_partialBody() {
        val person = SyntheticPerson(1.75f, personZ = -2.5f)
        val r = estimator.estimate(person.frame(Pose(Vec3(0f, 1.4f, 0f))))
        assertEquals(HeightReason.PARTIAL_BODY, r.reason)
        assertNull(r.heightMeters)
    }

    @Test fun lowLandmarkLikelihood_partialOrNoPerson() {
        val person = SyntheticPerson(1.75f, personZ = -2.5f)
        val f = person.frame(portraitPose(1.2f))
        val noAnkles = f.landmarks.points.mapValues { (k, l) ->
            if (k == BodyLandmark.LEFT_ANKLE) l.copy(inFrameLikelihood = 0.2f) else l
        }
        assertEquals(HeightReason.PARTIAL_BODY, estimator.estimate(f.copy(landmarks = f.landmarks.copy(points = noAnkles))).reason)
        assertEquals(HeightReason.NO_PERSON, estimator.estimate(person.frame(portraitPose(1.2f), likelihood = 0.1f)).reason)
        assertEquals(HeightReason.NO_PERSON, estimator.estimate(f.copy(landmarks = PoseLandmarks.empty(640, 480))).reason)
    }

    @Test fun tooFar_andTooClose() {
        val far = SyntheticPerson(1.75f, personZ = -6f)
        assertEquals(HeightReason.TOO_FAR, estimator.estimate(far.frame(portraitPose(1.2f))).reason)
        val close = SyntheticPerson(1.0f, personZ = -1.2f)
        assertEquals(HeightReason.TOO_CLOSE, estimator.estimate(close.frame(portraitPose(0.6f))).reason)
    }

    @Test fun leaningPerson_notUpright() {
        val person = SyntheticPerson(1.75f, personZ = -2.5f, headShiftX = 0.45f, shoulderShiftX = 0.35f)
        assertEquals(HeightReason.NOT_UPRIGHT, estimator.estimate(person.frame(portraitPose(1.2f))).reason)
    }

    @Test fun noDepthOrTracking_noNumber() {
        val person = SyntheticPerson(1.75f, personZ = -2.5f)
        val f = person.frame(portraitPose(1.2f), withDepth = false)
        assertEquals(HeightReason.LOW_DEPTH_CONFIDENCE, estimator.estimate(f).reason)
        val lost = person.frame(portraitPose(1.2f)).copy(tracking = TrackingStatus.PAUSED)
        assertEquals(HeightReason.TRACKING_POOR, estimator.estimate(lost).reason)
    }

    @Test fun noFaceDepth_usesTorsoBodyPlane() {
        val person = SyntheticPerson(1.75f, personZ = -3f)
        val f = person.frame(portraitPose(1.5f, pitchDownDeg = 15f))
        val noFace = DepthLookup { u, v -> f.depth!!.pointAt(u, v)?.takeIf { it.position.y < 1.5f } }
        assertOk(estimator.estimate(f.copy(depth = noFace)), 1.75f, 0.01f)
    }

    @Test fun eyesHidden_usesEars() {
        val person = SyntheticPerson(1.75f, personZ = -2.5f)
        val f = person.frame(portraitPose(1.2f))
        val noEyes = f.landmarks.points.filterKeys { it != BodyLandmark.LEFT_EYE && it != BodyLandmark.RIGHT_EYE }
        assertOk(estimator.estimate(f.copy(landmarks = f.landmarks.copy(points = noEyes))), 1.75f, 0.01f)
    }
}
