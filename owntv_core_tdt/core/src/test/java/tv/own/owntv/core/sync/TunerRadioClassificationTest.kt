package tv.own.owntv.core.sync

import android.media.tv.TvContract
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerRadioClassificationTest {
    @Test
    fun audioServiceTypeTakesPrecedenceOverName() {
        assertTrue(TunerRadioClassification.suggestRadio("Service 1", TvContract.Channels.SERVICE_TYPE_AUDIO))
    }

    @Test
    fun radioNamesStillWorkWhenTunerLabelsThemAsVideo() {
        assertTrue(TunerRadioClassification.suggestRadio("Rádio 3", TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO))
        assertTrue(TunerRadioClassification.suggestRadio("Cadena SER Madrid", null))
        assertTrue(TunerRadioClassification.suggestRadio("LOS40", null))
    }

    @Test
    fun televisionStaysTelevision() {
        assertFalse(TunerRadioClassification.suggestRadio("Radio Marca TV", null))
        assertFalse(TunerRadioClassification.suggestRadio("La 1 HD", null))
    }
}
