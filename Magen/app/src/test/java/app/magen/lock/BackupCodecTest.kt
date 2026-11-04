package app.magen.lock

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    @Test fun plainBackupRoundTrips() {
        val source = JSONObject().put("lockedApps", JSONObject().put("com.example.app", "PIN"))
        val decoded = BackupCodec.decode(BackupCodec.encode(source))
        assertEquals("PIN", decoded.getJSONObject("lockedApps").getString("com.example.app"))
    }

    @Test fun encryptedBackupRoundTrips() {
        val source = JSONObject().put("blockPlay", true)
        val bytes = BackupCodec.encode(source, "long-enough-passphrase".toCharArray())
        assertEquals(true, BackupCodec.decode(bytes, "long-enough-passphrase".toCharArray()).getBoolean("blockPlay"))
    }

    @Test fun wrongPasswordIsRejected() {
        val bytes = BackupCodec.encode(JSONObject().put("blockApk", true), "correct-password".toCharArray())
        assertThrows(BackupException::class.java) {
            BackupCodec.decode(bytes, "incorrect-password".toCharArray())
        }
    }

    @Test fun secretFieldsAreNeverExported() {
        assertThrows(BackupException::class.java) {
            BackupCodec.encode(JSONObject().put("hash", "not-allowed"))
        }
    }

    @Test fun damagedFileIsRejected() {
        assertThrows(BackupException::class.java) {
            BackupCodec.decode("not json".toByteArray())
        }
    }
}
