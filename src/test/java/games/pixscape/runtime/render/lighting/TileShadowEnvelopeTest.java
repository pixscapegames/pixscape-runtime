package games.pixscape.runtime.render.lighting;

import org.junit.Test;
import static org.junit.Assert.*;

public class TileShadowEnvelopeTest {
 @Test public void transparentMarginsNoLongerRejectAndInternalBowExtremaAreRetained(){
  float[] q={-50,-20,-50,180,50,180,50,-20};TileShadowGeometry g=new TileShadowGeometry(q,0,0,0,1,1,0,20,60);float[] full=new float[6],tight=new float[6];TileShadowEnvelope.bounds(g,null,.5f,full);
  AlphaBounds b=new AlphaBounds();b.minU=.25f;b.maxU=.75f;b.minV=.1f;b.maxV=.4f;b.sourceWidth=100;b.sourceHeight=200;assertTrue(TileShadowEnvelope.bounds(g,b,.5f,tight));assertTrue(tight[5]<full[5]);
  // Enumerate the existing carrier at dense light coefficients; the cold box must contain every UV-interior vertex.
  for(int j=-100;j<=100;j++){LightingFrame.Light l=new LightingFrame.Light();l.x=200+j;l.y=-200+j;l.z=30;float[] v=g.forLight(l);for(int i=0;i<v.length;i+=8)if(v[i+3]>=b.minU&&v[i+3]<=b.maxU&&v[i+4]>=b.minV&&v[i+4]<=b.maxV)for(int a=0;a<3;a++)assertTrue(v[i+a]>=tight[a]-.0001f&&v[i+a]<=tight[a+3]+.0001f);}
  float[] mismatch=new float[6];TileShadowEnvelope.bounds(g,b,.2f,mismatch);assertArrayEquals(full,mismatch,0);b.empty=true;assertFalse(TileShadowEnvelope.bounds(g,b,.5f,tight));
 }
}
