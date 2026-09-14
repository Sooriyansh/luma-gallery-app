package com.example.g.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

public class ImageEvidenceFusionTest {
    @Test public void cameraPhotoDoesNotAutomaticallyBecomeAi(){
        ImageEvidenceFusion.Input in=input(MediaSourceEvidence.CAMERA);in.cameraScore=82;
        assertNotEquals(AnalysisStatus.LIKELY_AI,ImageEvidenceFusion.fuse(in).status);
    }
    @Test public void screenshotDoesNotAutomaticallyBecomeAi(){
        ImageEvidenceFusion.Input in=input(MediaSourceEvidence.SCREENSHOT);in.screenshotScore=90;
        ImageEvidenceFusion.Output out=ImageEvidenceFusion.fuse(in);
        assertEquals("Screenshot",out.mediaType);assertEquals(AnalysisStatus.UNKNOWN,out.status);
    }
    @Test public void missingExifDoesNotAutomaticallyBecomeAi(){assertEquals(AnalysisStatus.UNKNOWN,ImageEvidenceFusion.fuse(input(MediaSourceEvidence.OTHER)).status);}
    @Test public void pngDoesNotAutomaticallyBecomeAi(){assertNotEquals(AnalysisStatus.LIKELY_AI,ImageEvidenceFusion.fuse(input(MediaSourceEvidence.OTHER)).status);}
    @Test public void downloadedImageDoesNotAutomaticallyBecomeAi(){assertEquals(AnalysisStatus.UNKNOWN,ImageEvidenceFusion.fuse(input(MediaSourceEvidence.DOWNLOADED)).status);}
    @Test public void whatsappImageDoesNotAutomaticallyBecomeAi(){assertEquals(AnalysisStatus.UNKNOWN,ImageEvidenceFusion.fuse(input(MediaSourceEvidence.SHARED)).status);}
    @Test public void uncertainVisualResultIsUnableToVerify(){
        ImageEvidenceFusion.Input in=input(MediaSourceEvidence.OTHER);in.visualModelAvailable=true;in.visualAiScore=55;
        assertEquals(AnalysisStatus.UNKNOWN,ImageEvidenceFusion.fuse(in).status);
    }
    @Test public void strongCameraEvidenceOverridesWeakVisualEvidence(){
        ImageEvidenceFusion.Input in=input(MediaSourceEvidence.CAMERA);in.cameraScore=90;in.visualModelAvailable=true;in.visualAiScore=18;
        assertEquals(AnalysisStatus.LIKELY_REAL,ImageEvidenceFusion.fuse(in).status);
    }
    @Test public void strongVisualEvidenceAloneCannotIdentifyAi(){
        ImageEvidenceFusion.Input in=input(MediaSourceEvidence.OTHER);in.visualModelAvailable=true;in.visualAiScore=94;
        assertEquals(AnalysisStatus.POSSIBLY_AI,ImageEvidenceFusion.fuse(in).status);
    }
    @Test public void corroboratedStrongAiEvidenceCanProduceLikelyAi(){
        ImageEvidenceFusion.Input in=input(MediaSourceEvidence.OTHER);in.visualModelAvailable=true;in.visualAiScore=94;in.generatorMetadataScore=90;
        assertEquals(AnalysisStatus.LIKELY_AI,ImageEvidenceFusion.fuse(in).status);
    }
    @Test public void screenshotDoesNotHideStrongVisualAiEvidence(){
        ImageEvidenceFusion.Input in=input(MediaSourceEvidence.SCREENSHOT);in.screenshotScore=90;in.visualModelAvailable=true;in.visualAiScore=92;
        ImageEvidenceFusion.Output out=ImageEvidenceFusion.fuse(in);assertEquals("Screenshot containing suspected AI-generated content",out.mediaType);assertEquals(AnalysisStatus.POSSIBLY_AI,out.status);
    }
    @Test public void cameraMetadataPenaltyIsCappedForStrongVisualAiEvidence(){
        ImageEvidenceFusion.Input in=input(MediaSourceEvidence.CAMERA);in.cameraScore=100;in.visualModelAvailable=true;in.visualAiScore=95;
        ImageEvidenceFusion.Output out=ImageEvidenceFusion.fuse(in);assertEquals(AnalysisStatus.UNKNOWN,out.status);
    }
    @Test public void verifiedEvidenceIsRequiredForNinetyFivePercent(){
        ImageEvidenceFusion.Input in=input(MediaSourceEvidence.OTHER);in.visualModelAvailable=true;in.visualAiScore=100;
        in.generatorMetadataScore=100;in.provenanceScore=100;
        assertEquals(94,ImageEvidenceFusion.fuse(in).aiProbability);
        in.provenanceVerified=true;
        assertEquals(AnalysisStatus.LIKELY_AI,ImageEvidenceFusion.fuse(in).status);
    }
    @Test public void probabilitiesAlwaysComplementEachOther(){
        ImageEvidenceFusion.Output out=ImageEvidenceFusion.fuse(input(MediaSourceEvidence.OTHER));
        assertEquals(100,out.aiProbability+out.realProbability);
    }
    private static ImageEvidenceFusion.Input input(String source){ImageEvidenceFusion.Input in=new ImageEvidenceFusion.Input();in.sourceType=source;return in;}
}
