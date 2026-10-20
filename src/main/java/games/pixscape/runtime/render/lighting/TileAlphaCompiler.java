package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;

/** Cold, geometry-only admission. Image pixels and asset identities never choose a support.
 * Full carriers use a volume contact plane; mixed carriers reuse the existing disjoint face partition.
 * The returned masks belong to the receiver cache, which is the sole cell authority.
 */
final class TileAlphaCompiler {
    static final class Cell {
        final int gx,gy;
        final float[] quad;
        final Array<SpatialBlockData> owners;
        final LightingSurface surface;
        final boolean full;
        final String reason;
        final int horizontal,vertical,fallback;
        final float[] replacement;
        Cell(int gx,int gy,float[] quad,Array<SpatialBlockData> owners,LightingSurface surface,boolean full,
             String reason,int horizontal,int vertical,int fallback){
            this(gx,gy,quad,owners,surface,full,reason,horizontal,vertical,fallback,null);
        }
        Cell(int gx,int gy,float[] quad,Array<SpatialBlockData> owners,LightingSurface surface,boolean full,
             String reason,int horizontal,int vertical,int fallback,float[] replacement){
            this.gx=gx;this.gy=gy;this.quad=quad;this.owners=owners;this.surface=surface;this.full=full;
            this.reason=reason;this.horizontal=horizontal;this.vertical=vertical;this.fallback=fallback;
            this.replacement=replacement;
        }
    }
    static Cell prepare(TiledMapLayerData map,int gx,int gy,float[] quad,Array<SpatialBlockData> owners,
                        LightingSurface legacy,TileShadowMode mode,TileAlphaContactIndex.MapContacts contacts){
        if(map.projection!=TiledProjection.ISO||map.tileWidth!=2*map.tileHeight)
            return refused(gx,gy,quad,owners,legacy,"Unsupported projection or metric");
        if(Math.abs(quad[0]+quad[4]-quad[2]-quad[6])>.01f||Math.abs(quad[1]+quad[5]-quad[3]-quad[7])>.01f)
            return refused(gx,gy,quad,owners,legacy,"Non-affine image placement");
        if(owners==null||owners.size==0){
            legacy.alphaSheet=new TileAlphaSheet(legacy.vertices,new TileAlphaSheet.Part[0],mode);
            return new Cell(gx,gy,quad,owners,legacy,true,"Unlinked horizontal receiver; no invented caster",legacy.vertices.length/24,0,0);
        }
        TileShadowSupport support=new TileShadowSupport(map,gx,gy,quad,owners,legacy,false);
        boolean caster=false,noncaster=false;
        for(int i=0;i<owners.size;i++){SpatialBlockData b=owners.get(i);if(b.shadowCaster||b.lightOccluder)caster=true;else noncaster=true;}
        if(!caster)return refused(gx,gy,quad,owners,legacy,"No enabled caster volume");
        if(noncaster)support.rejection="Associated supports have different caster participation";
        if(support.rejection!=null&&!noncaster){Cell joint=TileAlphaJunction.prepare(map,gx,gy,quad,owners,legacy,mode,contacts);if(joint!=null)return joint;}
        if(support.rejection==null){
            float nx=support.normalX,ny=support.normalY,nz=support.normalZ,den=nz-nx-ny;
            if(Math.abs(den)>.00001f){
                float[] data=new float[48];float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
                for(int i=0;i<6;i++){
                    int c=SpriteSurfaceSupport.corner(i),o=i*8;float u=c>=2?1:0,v=c==1||c==2?1:0;
                    float sx=quad[c*2],sy=quad[c*2+1];
                    float z=(float)(((double)nx*support.contactX+(double)ny*support.contactY+(double)nz*support.contactZ
                            -(nx+ny)*(double)sy-(nx-ny)*(double)sx*.5)/den);
                    data[o]=LightingCoordinates.worldX(sx,sy,z);data[o+1]=LightingCoordinates.worldY(sx,sy,z);data[o+2]=z;
                    data[o+3]=u;data[o+4]=v;data[o+5]=nx;data[o+6]=ny;data[o+7]=nz;
                    min=Math.min(min,z);max=Math.max(max,z);
                }
                if(nz>.999f || support.height>0&&min<=support.contactZ+.01f&&max>=support.contactZ-.01f){
                    float[] receiverFaces=nz==0&&(support.orientation==TileShadowSupport.X_NORMAL||support.orientation==TileShadowSupport.Y_NORMAL)?receiverFaces(legacy.vertices):null;
                    // Reuse the view-dependent V2 partition without changing the affine alpha plane.
                    // Both caster and receiver consume these same triangles; caps/unknown regions keep their contract.
                    if(receiverFaces!=null)data=partitionSheet(data,legacy.vertices);
                    LightingSurface s=new LightingSurface(data);s.approximate=true;s.shadowCaster=true;
                    TileAlphaSheet.Part p=new TileAlphaSheet.Part(0,data.length,support.contactX,support.contactY,support.contactZ,
                            nx,ny,nz,nz>.999f?0:support.height,support.thickness*.5f);
                    TileAlphaContactIndex.Band band=mode==TileShadowMode.V3_PLANE||nz!=0?null:band(contacts,owners,nx,ny);
                    if(band!=null)p=new TileAlphaSheet.Part(0,data.length,band.x,band.y,band.z,nx,ny,nz,band.height,band.halfThickness);
                    s.alphaSheet=new TileAlphaSheet(data,new TileAlphaSheet.Part[]{p},mode,null,receiverFaces);
                    return new Cell(gx,gy,quad,owners,s,true,null,nz>.999f?data.length/24:0,nz>.999f?0:data.length/24,0);
                }
                support.rejection="Contact directrix does not cross the image";
            }else support.rejection="Support is edge-on to image projection";
        }
        // At a corner, independently shearing adjoining face partitions opens a seam.
        // Keep those vertical portions V2 in inclined modes; plane mode can reuse them exactly.
        float[] data=legacy.receiverVertices!=null?legacy.receiverVertices:legacy.vertices;
        boolean[] admitted=new boolean[data.length/24];Array<TileAlphaSheet.Part> parts=new Array<TileAlphaSheet.Part>();
        int horizontal=0,vertical=0,fallback=0;
        for(int i=0;i<data.length;i+=24){
            float[] v=legacy.vertices;boolean valid=v[i+5]!=0||v[i+6]!=0||v[i+7]!=0;
            boolean floor=valid&&Math.abs(v[i+7])>.999f;
            if(valid&&enabledFace(map,v,i,owners)&&(floor||mode==TileShadowMode.V3_PLANE)){
                admitted[i/24]=true;if(floor)horizontal++;else vertical++;
                parts.add(new TileAlphaSheet.Part(i,i+24,data[i],data[i+1],data[i+2],data[i+5],data[i+6],data[i+7],0,0));
            }else fallback++;
        }
        if(parts.size==0)return refused(gx,gy,quad,owners,legacy,support.rejection);
        TileAlphaSheet.Part[] ranges=new TileAlphaSheet.Part[parts.size];
        for(int i=0;i<ranges.length;i++)ranges[i]=parts.get(i);
        legacy.alphaSheet=new TileAlphaSheet(data,ranges,mode,admitted,noncaster?null:receiverFaces(legacy.vertices));
        legacy.shadowCaster=true;
        return new Cell(gx,gy,quad,owners,legacy,false,support.rejection+"; mixed vertical portions retain V2 in inclined modes",horizontal,vertical,fallback);
    }
    /** Native oriented volume faces, independent of the shading normal and of the inclined carrier. */
    static float[] receiverFaces(float[] raw){
        float[] faces=null;
        for(int at=0;at<raw.length;at+=24){
            float nx=raw[at+5],ny=raw[at+6];int side=Math.abs(nx)>.999f?(nx<0?1:2):Math.abs(ny)>.999f?(ny<0?3:4):0;
            if(side==0)continue;if(faces==null)faces=new float[raw.length/12];int i=at/24*2;faces[i]=side;faces[i+1]=raw[at+(side<=2?0:1)];
        }
        return faces;
    }
    private static float[] partitionSheet(float[] sheet,float[] partition){
        float[] result=new float[partition.length];
        for(int at=0;at<result.length;at+=8){
            double u=partition[at+3],v=partition[at+4];
            for(int k=0;k<3;k++)result[at+k]=(float)(sheet[k]+u*((double)sheet[40+k]-sheet[k])+v*((double)sheet[8+k]-sheet[k]));
            result[at+3]=partition[at+3];result[at+4]=partition[at+4];result[at+5]=sheet[5];result[at+6]=sheet[6];result[at+7]=sheet[7];
        }
        return result;
    }
    private static TileAlphaContactIndex.Band band(TileAlphaContactIndex.MapContacts contacts,Array<SpatialBlockData> owners,float nx,float ny){
        if(contacts==null||owners==null)return null;
        TileAlphaContactIndex.Band selected=null;
        for(int i=0;i<owners.size;i++){
            TileAlphaContactIndex.Band b=contacts.band(owners.get(i).id);
            if(b==null||b.nx!=nx||b.ny!=ny||selected!=null&&selected!=b)return null;
            selected=b;
        }
        return selected;
    }
    private static boolean enabledFace(TiledMapLayerData map,float[] v,int offset,Array<SpatialBlockData> owners){
        float x=(v[offset]+v[offset+8]+v[offset+16])/3,y=(v[offset+1]+v[offset+9]+v[offset+17])/3,z=(v[offset+2]+v[offset+10]+v[offset+18])/3;
        float[] p=new float[3];
        for(int i=0;i<owners.size;i++){
            SpatialBlockData b=owners.get(i);if(!b.shadowCaster&&!b.lightOccluder)continue;
            LightingCoordinates.mapPoint(map,b.x,b.y,0,p,0);float xx=p[0],yy=p[1];
            LightingCoordinates.mapPoint(map,b.x+b.width,b.y+b.depth,0,p,0);
            if(x<xx-.01f||x>p[0]+.01f||y<yy-.01f||y>p[1]+.01f||z<b.altitude-.01f||z>b.altitude+b.height+.01f)continue;
            if(Math.abs(v[offset+5])>.9f&&(Math.abs(x-xx)<.01f||Math.abs(x-p[0])<.01f)
                    ||Math.abs(v[offset+6])>.9f&&(Math.abs(y-yy)<.01f||Math.abs(y-p[1])<.01f)
                    ||Math.abs(v[offset+7])>.9f&&(Math.abs(z-b.altitude)<.01f||Math.abs(z-b.altitude-b.height)<.01f))return true;
        }
        return false;
    }
    static Cell refused(int gx,int gy,float[] quad,Array<SpatialBlockData> owners,LightingSurface s,String reason){
        return new Cell(gx,gy,quad,owners,s,false,reason,0,0,s.vertices.length/24);
    }

    static final class Patch implements Comparable<Patch>{
        final Cell cell;final float u0,v0,u1,v1;
        Patch(Cell cell,float u0,float v0,float u1,float v1){this.cell=cell;this.u0=u0;this.v0=v0;this.u1=u1;this.v1=v1;}
        public int compareTo(Patch p){int c=Float.compare(v0,p.v0);return c==0?Float.compare(u0,p.u0):c;}
    }
    /** Six faces per volume, sparse disjoint cell patches per face. O(K log K + output), not
     * repeated subtraction of a growing whole-face polygon list. UV clipping is bounded to one cell.
     */
    static int replace(TiledMapLayerData map,LightingSurface face,Array<Cell> cells,Array<LightingSurface> out){
        int reference=axisReference(face.sourceBlockId,cells);
        if(reference>=0)return replaceAxial(map,face,cells,out,reference);
        float[] f=face.vertices;
        int axis=Math.abs(f[5])>.9f?0:Math.abs(f[6])>.9f?1:2;
        int ua=axis==0?1:0,va=axis==2?1:2;
        float u0=Float.POSITIVE_INFINITY,v0=u0,u1=Float.NEGATIVE_INFINITY,v1=u1;
        for(int i=0;i<f.length;i+=8){u0=Math.min(u0,f[i+ua]);u1=Math.max(u1,f[i+ua]);v0=Math.min(v0,f[i+va]);v1=Math.max(v1,f[i+va]);}
        Array<Patch> patches=new Array<Patch>();float[] a=new float[3],b=new float[3];
        for(int i=0;i<cells.size;i++){
            Cell cell=cells.get(i);
            // The source face already identifies its block; derive that block's cell box from the face extents.
            LightingCoordinates.mapPoint(map,cell.gx,cell.gy,0,a,0);LightingCoordinates.mapPoint(map,cell.gx+1,cell.gy+1,0,b,0);
            if(axis==0&&(f[0]<a[0]-.001f||f[0]>b[0]+.001f)||axis==1&&(f[1]<a[1]-.001f||f[1]>b[1]+.001f))continue;
            float lo=Math.max(u0,a[ua]),hi=Math.min(u1,b[ua]);
            float bottom=axis==2?Math.max(v0,a[1]):v0,top=axis==2?Math.min(v1,b[1]):v1;
            if(hi-lo>.0001f&&top-bottom>.0001f)patches.add(new Patch(cell,lo,bottom,hi,top));
        }
        if(patches.size==0){out.add(face);return 0;}
        int outputStart=out.size;
        patches.sort();int at=0,replaced=0;float row=v0;
        Array<float[]> fragments=new Array<float[]>();
        while(at<patches.size){
            Patch first=patches.get(at);float bottom=first.v0,top=first.v1;
            emit(face,rectangle(f,axis,ua,va,u0,row,u1,bottom),out);
            float cursor=u0;
            while(at<patches.size&&Math.abs(patches.get(at).v0-bottom)<.0001f){
                Patch p=patches.get(at++);
                emit(face,rectangle(f,axis,ua,va,cursor,bottom,p.u0,top),out);
                float[] patch=rectangle(f,axis,ua,va,p.u0,bottom,p.u1,top);
                for(int k=0;k<patch.length;k+=8)MapLightingGeometry.imageUV(p.cell.quad,LightingCoordinates.screenX(patch[k],patch[k+1]),LightingCoordinates.screenY(patch[k],patch[k+1],patch[k+2]),patch,k+3);
                fragments.clear();fragments.add(patch);
                float before=area(fragments);
                if(p.cell.full){
                    float[] mask=new float[32];mask[12]=1;mask[19]=mask[20]=1;mask[27]=1;
                    fragments=subtract(fragments,mask);
                }else{
                    float[] source=p.cell.surface.vertices;
                    for(int k=0;k<source.length;k+=24){
                        if(!p.cell.surface.alphaSheet.admits(k)||Math.abs(source[k+5]*f[5]+source[k+6]*f[6]+source[k+7]*f[7])<.999f||Math.abs(source[k+axis]-f[axis])>.01f)continue;
                        float[] mask=new float[24];System.arraycopy(source,k,mask,0,24);fragments=subtract(fragments,mask);
                    }
                }
                if(area(fragments)<before-.000001f)replaced++;
                for(int k=0;k<fragments.size;k++)emit(face,fragments.get(k),out);
                cursor=p.u1;
            }
            emit(face,rectangle(f,axis,ua,va,cursor,bottom,u1,top),out);row=top;
        }
        emit(face,rectangle(f,axis,ua,va,u0,row,u1,v1),out);
        merge(face,out,outputStart,replaced);
        return replaced;
    }
    private static void merge(LightingSurface face,Array<LightingSurface> out,int outputStart,int replaced){
        // Retain one source-face caster in the existing index, rather than one caster per cut fragment.
        int length=0;for(int i=outputStart;i<out.size;i++)length+=out.get(i).vertices.length;
        if(replaced==0){out.truncate(outputStart);out.add(face);}
        else if(length>0){
            float[] data=new float[length];int offset=0;
            for(int i=outputStart;i<out.size;i++){float[] v=out.get(i).vertices;System.arraycopy(v,0,data,offset,v.length);offset+=v.length;}
            out.truncate(outputStart);LightingSurface s=new LightingSurface(data);s.shadowCaster=true;s.receiveLight=false;s.sourceBlockId=face.sourceBlockId;out.add(s);
        }
    }
    private static final class Column implements Comparable<Column>{
        final float lo,hi;final Array<Cell> cells=new Array<Cell>();
        Column(float lo,float hi){this.lo=lo;this.hi=hi;}
        public int compareTo(Column other){return Float.compare(lo,other.lo);}
    }
    private static final java.util.Comparator<Cell> CELL_ORDER=new java.util.Comparator<Cell>(){
        public int compare(Cell a,Cell b){int c=Integer.compare(a.gx,b.gx);return c!=0?c:Integer.compare(a.gy,b.gy);}
    };
    private static int sheetAxis(Cell cell){
        if(!cell.full||cell.surface.alphaSheet==null||cell.surface.alphaSheet.parts.length!=1)return -1;
        TileAlphaSheet.Part p=cell.surface.alphaSheet.parts[0];
        return p.nz!=0?-1:p.nx==1&&p.ny==0?0:p.nx==0&&p.ny==1?1:-1;
    }
    private static float replacementPlane(Cell c,int axis){
        if(c.replacement==null)return Float.NaN;float[] v=c.replacement;
        for(int i=0;i<v.length;i+=24)if(Math.abs(v[i+5+axis])>.999f)return v[i+axis];return Float.NaN;
    }
    private static int axisReference(int block,Array<Cell> cells){
        for(int i=0;i<cells.size;i++){Cell c=cells.get(i);if(c.replacement==null||c.owners==null)continue;
            for(int j=0;j<c.owners.size;j++){SpatialBlockData b=c.owners.get(j);if(b.id!=block)continue;int axis=b.depth>=2*b.width?0:b.width>=2*b.depth?1:-1;if(axis>=0&&replacementPlane(c,axis)==replacementPlane(c,axis))return axis;}
        }
        int result=-1;
        for(int i=0;i<cells.size;i++){int axis=sheetAxis(cells.get(i));if(axis>=0){if(result>=0&&axis!=result)return -1;result=axis;}}
        return result;
    }
    /** A represented wall column spans the volume thickness, not the transverse grid cell.
     * Longitudinal ownership remains disjoint; only overlapping masks in the same column are
     * combined. This avoids subtracting every image from a growing whole-wall fragment list.
     * Everything here runs at the existing cold alpha publication boundary.
     */
    private static int replaceAxial(TiledMapLayerData map,LightingSurface face,Array<Cell> cells,Array<LightingSurface> out,int normal){
        float[] f=face.vertices;int axis=Math.abs(f[5])>.9f?0:Math.abs(f[6])>.9f?1:2,ua=axis==0?1:0,va=axis==2?1:2,longitudinal=1-normal;
        float u0=Float.POSITIVE_INFINITY,v0=u0,u1=Float.NEGATIVE_INFINITY,v1=u1;
        for(int i=0;i<f.length;i+=8){u0=Math.min(u0,f[i+ua]);u1=Math.max(u1,f[i+ua]);v0=Math.min(v0,f[i+va]);v1=Math.max(v1,f[i+va]);}
        boolean end=axis==longitudinal;float minimum=longitudinal==ua?u0:v0,maximum=longitudinal==ua?u1:v1;
        IntMap<Column> byCell=new IntMap<Column>();Array<Column> columns=new Array<Column>();float[] a=new float[3],b=new float[3];
        for(int i=0;i<cells.size;i++){
            Cell cell=cells.get(i);LightingCoordinates.mapPoint(map,cell.gx,cell.gy,0,a,0);LightingCoordinates.mapPoint(map,cell.gx+1,cell.gy+1,0,b,0);
            float representedLo=a[longitudinal],representedHi=b[longitudinal];
            if(cell.replacement!=null){
                representedLo=Float.POSITIVE_INFINITY;representedHi=Float.NEGATIVE_INFINITY;float[] source=cell.replacement;
                for(int k=0;k<source.length;k+=24){
                    boolean wall=Math.abs(source[k+5+normal])>.999f,facePart=Math.abs(source[k+5]*f[5]+source[k+6]*f[6]+source[k+7]*f[7])>.999f&&Math.abs(source[k+axis]-f[axis])<.01f;
                    if(!wall&&!facePart)continue;
                    for(int n=k;n<k+24;n+=8){representedLo=Math.min(representedLo,source[n+longitudinal]);representedHi=Math.max(representedHi,source[n+longitudinal]);}
                }
            }
            if(end&& (f[longitudinal]<representedLo-.001f||f[longitudinal]>representedHi+.001f))continue;
            float step=b[longitudinal]-a[longitudinal];
            int first=end?0:(int)Math.floor((Math.max(minimum,representedLo)-a[longitudinal])/step),last=end?0:(int)Math.ceil(((double)Math.min(maximum,representedHi)-a[longitudinal])/step)-1;
            for(int offset=first;offset<=last;offset++){
                float lo=Math.max(minimum,a[longitudinal]+offset*step),hi=Math.min(maximum,b[longitudinal]+offset*step);if(!end&&hi-lo<=.00001f)continue;
                int key=end?0:(longitudinal==0?cell.gx:cell.gy)+offset;Column column=byCell.get(key);
                if(column==null){column=new Column(lo,hi);byCell.put(key,column);columns.add(column);}column.cells.add(cell);
            }
        }
        if(columns.size==0){out.add(face);return 0;}
        columns.sort();int start=out.size,replaced=0;float cursor=minimum;
        for(int i=0;i<columns.size;i++){
            Column column=columns.get(i);column.cells.sort(CELL_ORDER);
            if(!end)emit(face,slice(f,axis,ua,va,longitudinal,cursor,column.lo,u0,v0,u1,v1),out);
            float[] patch=end?rectangle(f,axis,ua,va,u0,v0,u1,v1):slice(f,axis,ua,va,longitudinal,column.lo,column.hi,u0,v0,u1,v1);
            faceUV(patch,ua,va,u0,v0);Array<float[]> fragments=new Array<float[]>();fragments.add(patch);
            for(int j=0;j<column.cells.size&&fragments.size>0;j++){
                Cell cell=column.cells.get(j);
                if(cell.full){
                    float[] mask;
                    if(sheetAxis(cell)==normal){
                        mask=new float[patch.length];System.arraycopy(patch,0,mask,0,patch.length);TileAlphaSheet.Part reference=cell.surface.alphaSheet.parts[0];
                        // Project along the original thickness direction. Undoing the common shear
                        // leaves longitudinal coordinate and altitude invariant, so this is light independent.
                        for(int k=0;k<mask.length;k+=8){float x=normal==0?reference.x:mask[k],y=normal==1?reference.y:mask[k+1];
                            MapLightingGeometry.imageUV(cell.quad,LightingCoordinates.screenX(x,y),LightingCoordinates.screenY(x,y,mask[k+2]),mask,k+3);}
                    }else{
                        mask=cellPatch(map,cell,f,axis,ua,va,u0,v0,u1,v1,a,b);
                        for(int k=0;k<mask.length;k+=8)MapLightingGeometry.imageUV(cell.quad,LightingCoordinates.screenX(mask[k],mask[k+1]),LightingCoordinates.screenY(mask[k],mask[k+1],mask[k+2]),mask,k+3);
                    }
                    mask=MapLightingGeometry.clip(mask,3,0,true);mask=MapLightingGeometry.clip(mask,3,1,false);
                    mask=MapLightingGeometry.clip(mask,4,0,true);mask=MapLightingGeometry.clip(mask,4,1,false);faceUV(mask,ua,va,u0,v0);
                    double before=physicalArea(fragments,ua,va);fragments=subtract(fragments,mask);if(physicalArea(fragments,ua,va)<before-.00001)replaced++;
                }else{
                    float[] source=cell.replacement==null?cell.surface.vertices:cell.replacement;
                    float plane=replacementPlane(cell,normal);
                    if(plane==plane){
                        float[] mask=new float[patch.length];System.arraycopy(patch,0,mask,0,patch.length);
                        for(int k=0;k<mask.length;k+=8){float x=normal==0?plane:mask[k],y=normal==1?plane:mask[k+1];MapLightingGeometry.imageUV(cell.quad,LightingCoordinates.screenX(x,y),LightingCoordinates.screenY(x,y,mask[k+2]),mask,k+3);}
                        for(int k=0;k<source.length&&fragments.size>0;k+=24){
                            if(Math.abs(source[k+5+normal])<.999f)continue;float[] region=mask;
                            region=intersectReplacement(region,source,k);
                            faceUV(region,ua,va,u0,v0);double before=physicalArea(fragments,ua,va);fragments=subtract(fragments,region);if(physicalArea(fragments,ua,va)<before-.00001)replaced++;
                        }
                    }
                    for(int k=0;k<source.length&&fragments.size>0;k+=24){
                        if(!cell.surface.alphaSheet.admits(k)||Math.abs(source[k+5]*f[5]+source[k+6]*f[6]+source[k+7]*f[7])<.999f||Math.abs(source[k+axis]-f[axis])>.01f)continue;
                        float[] mask=new float[24];System.arraycopy(source,k,mask,0,24);faceUV(mask,ua,va,u0,v0);
                        double before=physicalArea(fragments,ua,va);fragments=subtract(fragments,mask);if(physicalArea(fragments,ua,va)<before-.00001)replaced++;
                    }
                }
            }
            for(int j=0;j<fragments.size;j++)emit(face,fragments.get(j),out);cursor=column.hi;
        }
        if(!end)emit(face,slice(f,axis,ua,va,longitudinal,cursor,maximum,u0,v0,u1,v1),out);
        merge(face,out,start,replaced);return replaced;
    }
    private static float[] slice(float[] f,int axis,int ua,int va,int longitudinal,float lo,float hi,float u0,float v0,float u1,float v1){
        return longitudinal==ua?rectangle(f,axis,ua,va,lo,v0,hi,v1):rectangle(f,axis,ua,va,u0,lo,u1,hi);
    }
    /** Strict double half-planes keep the V2 UV mask convex. An epsilon inside test with a
     * zero-distance intersection can otherwise extrapolate an edge when both distances are negative. */
    private static float[] intersectReplacement(float[] polygon,float[] triangle,int start){
        double sign=((double)triangle[start+11]-triangle[start+3])*(triangle[start+20]-triangle[start+4])
                -((double)triangle[start+19]-triangle[start+3])*(triangle[start+12]-triangle[start+4]);
        for(int edge=0;edge<3&&polygon.length>=24;edge++){
            int from=start+edge*8,to=start+(edge+1)%3*8,n=polygon.length/8,at=0;
            double dx=(double)triangle[to+3]-triangle[from+3],dy=(double)triangle[to+4]-triangle[from+4];
            double a=-dy,b=dx,c=dy*triangle[from+3]-dx*triangle[from+4];if(sign<0){a=-a;b=-b;c=-c;}
            float[] temp=new float[(n+2)*8];
            for(int i=0;i<n;i++){
                int j=(i+n-1)%n;double d0=a*polygon[j*8+3]+b*polygon[j*8+4]+c,d1=a*polygon[i*8+3]+b*polygon[i*8+4]+c;
                boolean in0=d0>=0,in1=d1>=0;
                if(in0!=in1){double t=d0/(d0-d1);for(int k=0;k<8;k++)temp[at++]=(float)(polygon[j*8+k]+t*((double)polygon[i*8+k]-polygon[j*8+k]));}
                if(in1){System.arraycopy(polygon,i*8,temp,at,8);at+=8;}
            }
            polygon=new float[at];System.arraycopy(temp,0,polygon,0,at);
        }
        return polygon;
    }
    private static float[] cellPatch(TiledMapLayerData map,Cell cell,float[] f,int axis,int ua,int va,float u0,float v0,float u1,float v1,float[] a,float[] b){
        LightingCoordinates.mapPoint(map,cell.gx,cell.gy,0,a,0);LightingCoordinates.mapPoint(map,cell.gx+1,cell.gy+1,0,b,0);
        if(axis==0&&(f[0]<a[0]-.001f||f[0]>b[0]+.001f)||axis==1&&(f[1]<a[1]-.001f||f[1]>b[1]+.001f))return new float[0];
        return rectangle(f,axis,ua,va,Math.max(u0,a[ua]),axis==2?Math.max(v0,a[1]):v0,Math.min(u1,b[ua]),axis==2?Math.min(v1,b[1]):v1);
    }
    private static void faceUV(float[] p,int ua,int va,float u0,float v0){for(int i=0;i<p.length;i+=8){p[i+3]=p[i+ua]-u0;p[i+4]=p[i+va]-v0;}}
    private static double physicalArea(Array<float[]> parts,int ua,int va){
        double area=0;for(int i=0;i<parts.size;i++){float[] p=parts.get(i);for(int j=8;j+8<p.length;j+=8)
            area+=Math.abs(((double)p[j+ua]-p[ua])*(p[j+8+va]-p[va])-((double)p[j+va]-p[va])*(p[j+8+ua]-p[ua]))*.5;}
        return area;
    }
    private static float area(Array<float[]> parts){
        float result=0;for(int k=0;k<parts.size;k++){
            float[] p=parts.get(k);float a=0;for(int i=0;i<p.length;i+=8){int j=(i+8)%p.length;a+=p[i+3]*p[j+4]-p[j+3]*p[i+4];}result+=Math.abs(a)*.5f;
        }return result;
    }
    private static Array<float[]> subtract(Array<float[]> parts,float[] mask){
        Array<float[]> next=new Array<float[]>();for(int i=0;i<parts.size;i++)MapLightingGeometry.subtract(parts.get(i),mask,next);return next;
    }
    private static float[] rectangle(float[] source,int axis,int ua,int va,float u0,float v0,float u1,float v1){
        if(u1-u0<=.00001f||v1-v0<=.00001f)return new float[0];
        float[] p=new float[32];for(int i=0;i<4;i++){
            int o=i*8;p[o+axis]=source[axis];p[o+ua]=i>=2?u1:u0;p[o+va]=i==1||i==2?v1:v0;
            p[o+5]=source[5];p[o+6]=source[6];p[o+7]=source[7];
        }return p;
    }
    private static void emit(LightingSurface original,float[] polygon,Array<LightingSurface> out){
        if(polygon.length<24)return;
        float[] data=MapLightingGeometry.triangulate(polygon,null);
        // Opaque retained geometry uses the existing white texel, not the alpha image's UV domain.
        for(int i=0;i<data.length;i+=8){data[i+3]=0;data[i+4]=0;}
        LightingSurface s=new LightingSurface(data);s.shadowCaster=true;s.receiveLight=false;s.sourceBlockId=original.sourceBlockId;out.add(s);
    }
}
