/*
 * ===========================================
 * Java Pdf Extraction Decoding Access Library
 * ===========================================
 *
 * Project Info:  http://www.idrsolutions.com
 * Help section for developers at http://www.idrsolutions.com/support/
 *
 * (C) Copyright 1997-2016 IDRsolutions and Contributors.
 *
 * This file is part of JPedal/JPDF2HTML5
 *
     This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


 *
 * ---------------
 * MaskObject.java
 * ---------------
 */
package org.jpedal.objects.raw;

import org.jpedal.utils.LogWriter;

public class MaskObject extends XObject {

	//unknown CMAP as String
	//String unknownValue=null;

	private float[] BC;

	//boolean ImageMask=false;

	//int FormType=0, Height=1, Width=1;

	private PdfObject G, TRasDict;

    public MaskObject(final String ref) {
        super(ref);

        objType=PdfDictionary.Mask;
    }

    public MaskObject(final int ref, final int gen) {
       super(ref,gen);

        objType=PdfDictionary.Mask;
    }



    @Override
    public PdfObject getDictionary(final int id){
       
        switch(id){

	        case PdfDictionary.G:
               // System.out.println(G+" returned from "+this+" "+this.getObjectRefAsString());
	        	return G;

            case PdfDictionary.TR:
                return TRasDict;

            default:
                return super.getDictionary(id);
        }
    }


    @Override
    public void setDictionary(final int id, final PdfObject value){

        switch(id){

	        case PdfDictionary.G:
                //System.out.println(this+" set G to "+G);
	        	G=value;
			break;

            case PdfDictionary.TR:
            	TRasDict=value;
            break;

            default:
            	super.setDictionary(id, value);
        }
    }


    @Override
    public int setConstant(final int pdfKeyType, final int keyStart, final int keyLength, final byte[] raw) {

        int PDFvalue =PdfDictionary.Unknown;

        int id=0,x=0,next;

        try{

            //convert token to unique key which we can lookup

            for(int i2=keyLength-1;i2>-1;i2--){

            	next=raw[keyStart+i2];

            	//System.out.println((char)next);
            	next -= 48;

                id += ((next)<<x);

                x += 8;
            }

            switch(id){

                default:
                	PDFvalue=super.setConstant(pdfKeyType,id);

                    if(PDFvalue==-1 && debug){

                        	 final byte[] bytes=new byte[keyLength];

                            System.arraycopy(raw,keyStart,bytes,0,keyLength);
                            System.out.println("key="+new String(bytes)+ ' ' +id+" not implemented in setConstant in "+this);

                            System.out.println("final public static int "+new String(bytes)+ '=' +id+ ';');
                           
                        }

                    break;

            }

        }catch(final Exception e){
            LogWriter.writeLog("Exception: " + e.getMessage());
        }

        switch(pdfKeyType){

            case PdfDictionary.SMask:
                generalType=PDFvalue;
                break;

            case PdfDictionary.TR:
                generalType=PDFvalue;
                break;

            default:
                super.setConstant(pdfKeyType,id);

        }

        return PDFvalue;
    }



//    public void setStream(){
//
//        hasStream=true;
//    }

    @Override
    public float[] getFloatArray(final int id) {


        
        switch(id){

            case PdfDictionary.BC:
            return deepCopy(BC);
            
            default:
            	return super.getFloatArray(id);

        }
    }

    @Override
    public void setFloatArray(final int id, final float[] value) {

        switch(id){

	        case PdfDictionary.BC:
	            BC=value;
	        break;

            default:
            	super.setFloatArray(id, value);
        }
    }



    /**
     * unless you need special fucntions,
     * use getStringValue(int id) which is faster
     */
    @Override
    public String getStringValue(final int id, final int mode) {

        final byte[] data=null;

        //get data
     //   switch(id){

//            case PdfDictionary.BaseFont:
//                data=rawBaseFont;
//                break;

      //  }


        //convert
        switch(mode){
            case PdfDictionary.STANDARD:

                //setup first time
                if(data!=null) {
                    return new String(data);
                } else {
                    return null;
                }


            case PdfDictionary.LOWERCASE:

                //setup first time
                if(data!=null) {
                    return new String(data);
                } else {
                    return null;
                }

            case PdfDictionary.REMOVEPOSTSCRIPTPREFIX:

                //setup first time
                if(data!=null){
                	final int len=data.length;
                	if(len>6 && data[6]=='+'){ //lose ABCDEF+ if present
                		final int length=len-7;
                		final byte[] newData=new byte[length];
                		System.arraycopy(data, 7, newData, 0, length);
                		return new String(newData);
                	}else {
                        return new String(data);
                    }
                }else {
                    return null;
                }

            default:
                throw new RuntimeException("Value not defined in getName(int,mode) in "+this);
        }
    }
}