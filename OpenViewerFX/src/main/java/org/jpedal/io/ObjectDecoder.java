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
 * ObjectDecoder.java
 * ---------------
 */
package org.jpedal.io;

import java.io.Serializable;
import org.jpedal.io.security.DecryptionFactory;
import org.jpedal.io.types.*;
import org.jpedal.objects.raw.PdfDictionary;
import org.jpedal.objects.raw.PdfObject;
import org.jpedal.utils.NumberUtils;
import org.jpedal.utils.StringUtils;

/**
 *
 */
public class ObjectDecoder implements Serializable {
    
    public PdfFileReader objectReader;
    
    public DecryptionFactory decryption;
    
    //string representation of key only used in debugging
    private Object PDFkey;
    
    static final byte[] endPattern = { 101, 110, 100, 111, 98, 106 }; //pattern endobj
    
    //not final in IDE but in build do our static analysis does not flag as dead debug code
    //which we  want compiler to ooptimise out
    public static boolean debugFastCode; //objRef.equals("68 0 R")
    
    private int pdfKeyType, PDFkeyInt;
    
    /**used in debugging output*/
    public static String padding="";
    
    boolean isInlineImage;
    
    private int endPt=-1;
    
    public ObjectDecoder(final PdfFileReader pdfFileReader) {
        init(pdfFileReader);
    }

    private void init(final PdfFileReader objectReader){
        this.objectReader=objectReader;
        
        this.decryption=objectReader.getDecryptionObject();
    }
    
    /**
     * read a dictionary object
     */
    public int readDictionaryAsObject(final PdfObject pdfObject, int i, final byte[] raw){
        
        if(endPt==-1) {
            endPt = raw.length;
        }
         
        //used to debug issues by printing out details for obj
        //(set to non-final above)
        //debugFastCode =pdfObject.getObjectRefAsString().equals("5 0 R");
         
        if(debugFastCode) {
            padding += "   ";
        }
        
        final int length=raw.length;
        
        //show details in debug mode
        if(debugFastCode) {
            ObjectUtils.showData(pdfObject, i, length, raw, padding);
        }
        
        /*
         * main loop for read all values from Object data and store in PDF object
         */
        i = readObjectDataValues(pdfObject, i, raw, length);
        
        /*
         * look for stream afterwards
         */
        if(!pdfObject.ignoreStream() && pdfObject.getGeneralType(-1)!=PdfDictionary.ID) {
            Stream.readStreamData(pdfObject, i, raw, length, objectReader);
        }
        
        /*
         * we need full names for Forms
         */
        if(pdfObject.getObjectType()==PdfDictionary.Form) {
            Form.setFieldNames(pdfObject, objectReader);
        }
        
        /*
         * reset indent in debugging
         */
        if(debugFastCode){
            final int len=padding.length();
            
            if(len>3) {
                padding = padding.substring(0, len - 3);
            }
        }
        
        return i;
        
    }
    
    /**
     * get the values from the data stream and store in PdfObject
     * @param pdfObject
     * @param i
     * @param raw
     * @param length
     * @return
     */
    private int readObjectDataValues(final PdfObject pdfObject, int i, final byte[] raw, final int length) {
        
        int level=0;
        //allow for no << at start
        if(isInlineImage) {
            level = 1;
        }
        
        while(true){
            
            if(i<length && raw[i]==37) //allow for comment and ignore
            {
                i = StreamReaderUtils.skipComment(raw, i);
            }
            
            /*
             * exit conditions
             */
            if ((i>=length ||
                    (endPt !=-1 && i>= endPt))||
                    (raw[i] == 101 && raw[i + 1] == 110 && raw[i + 2] == 100 && raw[i + 3] == 111)||
                    (raw[i]=='s' && raw[i+1]=='t' && raw[i+2]=='r' && raw[i+3]=='e' && raw[i+4]=='a' && raw[i+5]=='m')) {
                break;
            }
            
            /*
             * process value
             */
            if(raw[i]==60 && raw[i+1]==60){
                i++;
                level++;
            }else if(raw[i]==62 && i+1!=length && raw[i+1]==62){
                i++;
                level--;
                
                if(level==0) {
                    break;
                }
            }else if (raw[i] == 47 && (raw[i+1] == 47 || raw[i+1]==32)) { //allow for oddity of //DeviceGray  and / /DeviceGray in colorspace
                i++;
            }else  if (raw[i] == 47) { //everything from /
                
                i++; //skip /
                
                final int keyStart=i;
                final int keyLength= StreamReaderUtils.findDictionaryEnd(i, raw, length);
                i += keyLength;
                
                if(i==length) {
                    break;
                }
                
                //if BDC see if string
                boolean isStringPair=false;
                if(pdfObject.getID()== PdfDictionary.BDC) {
                    isStringPair = isStringPair(i, raw, isStringPair);
                }
                
                final int type=pdfObject.getObjectType();
                
                if(debugFastCode) {
                    System.out.println("type=" + type + ' ' + ' ' + pdfObject.getID() + " chars=" + (char) raw[i - 1] + (char) raw[i] + (char) raw[i + 1] + ' ' + pdfObject + " i=" + i + ' ' + isStringPair);
                }
                
                //see if map of objects
                final boolean isMap = isMapObject(pdfObject, i, raw, length, keyStart, keyLength, isStringPair, type);
                
                if(raw[i]==47 || raw[i]==40 || (raw[i] == 91 && raw[i+1]!=']')) //move back cursor
                {
                    i--;
                }
                
                //check for unknown value and ignore
                if(pdfKeyType==-1) {
                    i = ObjectUtils.handleUnknownType(i, raw, length);
                }
                
                /*
                 * now read value
                 */
                if(PDFkeyInt==-1 || pdfKeyType==-1){
                    if(debugFastCode) {
                        System.out.println(padding + pdfObject.getObjectRefAsString() + " =================Not implemented=" + PDFkey + " pdfKeyType=" + pdfKeyType);
                    }
                }else{
                    i = setValue(pdfObject, i, raw, length, isMap);
                }
            }
            
            i++;
            
        }
        
        return i;
    }
    
    private boolean isMapObject(final PdfObject pdfObject, final int i, final byte[] raw, final int length, final int keyStart, final int keyLength, final boolean stringPair, final int type) {
        
        final boolean isMap;//ensure all go into 'pool'
        if(type== PdfDictionary.MCID && (pdfObject.getID()==PdfDictionary.RoleMap ||
                (pdfObject.getID()==PdfDictionary.BDC && stringPair) ||
                (pdfObject.getID()==PdfDictionary.A && raw[i-2]=='/'))){
            
            pdfKeyType=PdfDictionary.VALUE_IS_NAME;
            
            //used in debug and this case
            PDFkey=PdfDictionary.getKey(keyStart,keyLength,raw);
            PDFkeyInt=PdfDictionary.MCID;
            isMap=true;
            
        }else{
            isMap=false;
            PDFkey=null;
            getKeyType(pdfObject, i, raw, length, keyLength, keyStart, type);
        }
        return isMap;
    }
    
    private void getKeyType(final PdfObject pdfObject, final int i, final byte[] raw, final int length, final int keyLength, final int keyStart, final int type) {
        /*
         * get Dictionary key and type of value it takes
         */
        if(debugFastCode)//used in debug
        {
            PDFkey = PdfDictionary.getKey(keyStart, keyLength, raw);
        }
            
        PDFkeyInt=PdfDictionary.getIntKey(keyStart,keyLength,raw);
        
        //correct mapping
        if(PDFkeyInt==PdfDictionary.Indexed && (type==PdfDictionary.MK ||type==PdfDictionary.Form || type==PdfDictionary.Linearized || type==PdfDictionary.Group)) {
            PDFkeyInt = PdfDictionary.I;
        }
        
        if(isInlineImage) {
            PDFkeyInt = PdfObjectFactory.getInlineID(PDFkeyInt);
        }
        
        final int id=pdfObject.getID();
        
        if ((type==PdfDictionary.Form || type==PdfDictionary.MK) && PDFkeyInt== PdfDictionary.D){
            if(id==PdfDictionary.AP || id==PdfDictionary.AA){
                pdfKeyType= PdfDictionary.VALUE_IS_VARIOUS;
            }else if(id==PdfDictionary.Win){
                pdfKeyType= PdfDictionary.VALUE_IS_TEXTSTREAM;
            }else{
                pdfKeyType = PdfDictionary.getKeyType(PDFkeyInt, type);
            }
        }else if ((type==PdfDictionary.Form || type==PdfDictionary.MK) && (id==PdfDictionary.AP || id==PdfDictionary.AA) && PDFkeyInt== PdfDictionary.A){
            pdfKeyType= PdfDictionary.VALUE_IS_VARIOUS;
        }else if(id==PdfDictionary.Win && pdfObject.getObjectType()==PdfDictionary.Form &&
                (PDFkeyInt==PdfDictionary.P || PDFkeyInt==PdfDictionary.O)){
            pdfKeyType= PdfDictionary.VALUE_IS_TEXTSTREAM;
        }else {
            pdfKeyType = PdfDictionary.getKeyType(PDFkeyInt, type);
        }
        
        //allow for other values in D,N,R definitions
        if(pdfKeyType==-1 && (id== PdfDictionary.ClassMap || id== PdfDictionary.Dests)){
            pdfKeyType = Dictionary.getPairedValues(pdfObject, i, raw, pdfKeyType, length, keyLength, keyStart);
        }else
            
            //@zain @bethan - you will need to disable here (do this 4th)
            //once done I can remove all this code and methods
            
            //allow for other values in D,N,R definitions as key pairs
            if(((((id==PdfDictionary.D && 1==1)))) &&
                    pdfObject.getParentID()==PdfDictionary.AP && pdfObject.getObjectType()==PdfDictionary.Form && raw[i]!='['  ){
               
                //get next non number/char value
                int ptr=i;
                while((raw[ptr]>=48 && raw[ptr]<58) || raw[ptr]==32){
                    ptr++;
                }
                
                //decide if pair
                if(raw[keyStart]=='L' && raw[keyStart+1]=='e' && raw[keyStart+2]=='n' && raw[keyStart+3]=='g' && raw[keyStart+4]=='t' && raw[keyStart+5]=='h'){
                }else if(raw[keyStart]=='O' && raw[keyStart+1]=='n'){
                }else if(raw[keyStart]=='O' && raw[keyStart+1]=='f' && raw[keyStart+2]=='f'){
                }else
                    if(raw[ptr]=='R'){
                        pdfKeyType = Dictionary.getPairedValues(pdfObject, i, raw, pdfKeyType, length, keyLength, keyStart);
                        
                        if(debugFastCode) {
                            System.out.println("new Returns " + pdfKeyType + " i=" + i);
                        }
                    }
            }
        
        /**/
        
        //DecodeParms can be an array as well as a dictionary so check next char and alter if so
        if(PDFkeyInt==PdfDictionary.DecodeParms) {
            pdfKeyType = setTypeForDecodeParams(i, raw, pdfKeyType);
        }
        
        if(debugFastCode && pdfKeyType==-1 &&  pdfObject.getObjectType()!=PdfDictionary.Page){
            System.out.println(id+" "+type);
            System.out.println(padding +PDFkey+" NO type setting for "+PdfDictionary.getKey(keyStart,keyLength,raw)+" id="+i);
        }
    }
    
    private int setValue(final PdfObject pdfObject,int i, final byte[] raw, final int length, final boolean map) {
        
        //if we only need top level do not read whole tree
        final boolean ignoreRecursion=pdfObject.ignoreRecursion();
        
        if(debugFastCode) {
            System.out.println(padding + pdfObject.getObjectRefAsString() + " =================Reading value for key=" + PDFkey + " (" + PDFkeyInt + ") type=" + PdfDictionary.showAsConstant(pdfKeyType) + " ignorRecursion=" + ignoreRecursion + ' ' + pdfObject);
        }
        
        //resolve now in this case as we need to ensure all parts present
        if(pdfKeyType==PdfDictionary.VALUE_IS_UNREAD_DICTIONARY && pdfObject.isDataExternal()) {
            pdfKeyType = PdfDictionary.VALUE_IS_DICTIONARY;
        }
        
        
        switch(pdfKeyType){
            
            //read text stream (this is text) and also special case of [] in W in CID Fonts
            case PdfDictionary.VALUE_IS_TEXTSTREAM:{
                i = TextStream.setTextStreamValue(pdfObject, i, raw, ignoreRecursion,PDFkeyInt, objectReader);
                break;
                
            }case PdfDictionary.VALUE_IS_NAMETREE:{
                i = Name.setNameTreeValue(pdfObject, i, raw, length, ignoreRecursion,PDFkeyInt,objectReader);
                break;
                
                //readDictionary keys << /A 12 0 R /B 13 0 R >>
            }case PdfDictionary.VALUE_IS_DICTIONARY_PAIRS:{
                i = Dictionary.setDictionaryValue(pdfObject, i, raw, length, ignoreRecursion,objectReader,PDFkeyInt);
                break;
                
                //Strings
            }case PdfDictionary.VALUE_IS_STRING_ARRAY:{
                final ArrayDecoder objDecoder=new StringArray(objectReader,i,raw);
                i=objDecoder.readArray(pdfObject, PDFkeyInt);
                break;
                
                //read Object Refs in [] (may be indirect ref)
            }case PdfDictionary.VALUE_IS_BOOLEAN_ARRAY:{
                final ArrayDecoder objDecoder=new BooleanArray(objectReader, i, raw);
                i=objDecoder.readArray(pdfObject, PDFkeyInt);
                break;
                
                //read Object Refs in [] (may be indirect ref)
            }case PdfDictionary.VALUE_IS_KEY_ARRAY:{
                final ArrayDecoder objDecoder=new KeyArray(objectReader, i, raw);
                i=objDecoder.readArray(pdfObject, PDFkeyInt);
                break;
                
                //read numbers in [] (may be indirect ref)
            }case PdfDictionary.VALUE_IS_MIXED_ARRAY:{
                final ArrayDecoder objDecoder=new Array(objectReader, i, PdfDictionary.VALUE_IS_MIXED_ARRAY, raw);
                i=objDecoder.readArray(pdfObject, PDFkeyInt);
                break;
                
                //read numbers in [] (may be indirect ref) same as Mixed but allow for recursion and store as objects
            }case PdfDictionary.VALUE_IS_OBJECT_ARRAY:{
                final ArrayDecoder objDecoder=new ObjectArray(objectReader, i, raw);
                i=objDecoder.readArray(pdfObject, PDFkeyInt);
                break;
                
                //read numbers in [] (may be indirect ref)
            }case PdfDictionary.VALUE_IS_DOUBLE_ARRAY:{
                final ArrayDecoder objDecoder=new DoubleArray(objectReader, i, raw);
                i=objDecoder.readArray(pdfObject,PDFkeyInt);
                break;
                
                //read numbers in [] (may be indirect ref)
            }case PdfDictionary.VALUE_IS_INT_ARRAY:{
                final ArrayDecoder objDecoder=new IntArray(objectReader, i, raw);
                i=objDecoder.readArray(pdfObject, PDFkeyInt);
                break;
                
                //read numbers in [] (may be indirect ref)
            }case PdfDictionary.VALUE_IS_FLOAT_ARRAY:{
                final ArrayDecoder objDecoder=new FloatArray(objectReader, i, raw);
                i=objDecoder.readArray(pdfObject, PDFkeyInt);
                break;
                
                //read String (may be indirect ref)
            }case PdfDictionary.VALUE_IS_NAME:{
                i = Name.setNameStringValue(pdfObject, i, raw, map,PDFkey, PDFkeyInt, objectReader);
                break;
                
                //read true or false
            }case PdfDictionary.VALUE_IS_BOOLEAN:{
                i = BooleanValue.set(pdfObject, i, raw, PDFkeyInt);
                break;
                
                //read known set of values
            }case PdfDictionary.VALUE_IS_STRING_CONSTANT:{
                i = StringValue.setStringConstantValue(pdfObject, i, raw,PDFkeyInt);
                break;
                
                //read known set of values
            }case PdfDictionary.VALUE_IS_STRING_KEY:{
                i = StringValue.setStringKeyValue(pdfObject, i, raw,PDFkeyInt);
                break;
                
                //read number (may be indirect ref)
            }case PdfDictionary.VALUE_IS_INT:{
                
                //roll on
                i++;
                
                i = StreamReaderUtils.skipSpacesOrOtherCharacter(raw, i, 47);
                
                i = NumberValue.setNumberValue(pdfObject, i, raw, PDFkeyInt,objectReader);
                break;
                
                //read float number (may be indirect ref)
            }case PdfDictionary.VALUE_IS_FLOAT:{
                i = FloatValue.setFloatValue(pdfObject, i, raw, length,PDFkeyInt,objectReader);
                break;
                
                //read known Dictionary object which may be direct or indirect
            }case PdfDictionary.VALUE_IS_UNREAD_DICTIONARY:{
                i = Dictionary.setUnreadDictionaryValue(pdfObject, i, raw,PDFkeyInt, isInlineImage);
                break;
                
            }case PdfDictionary.VALUE_IS_VARIOUS:{
                if(raw.length-5>0 && StreamReaderUtils.isNull(raw,i+1)){ //ignore null value and skip (ie /N null)
                    i += 5;
                }else{
                    i = setVariousValue(pdfObject, i, raw, length, PDFkeyInt, map, ignoreRecursion,objectReader);
                }
                break;
                
            }case PdfDictionary.VALUE_IS_DICTIONARY:{
                i = Dictionary.setDictionaryValue(pdfObject, i, raw, ignoreRecursion,PDFkeyInt,objectReader);
                break;
            }
        }
        return i;
    }
    
    int setVariousValue(final PdfObject pdfObject, int i, final byte[] raw, final int length, final int PDFkeyInt, final boolean map, final boolean ignoreRecursion, final PdfFileReader objectReader) {

        if(raw[i]!='<') {
            i++;
        }
        
        if(debugFastCode) {
            System.out.println(padding + "Various value (first char=" + (char) raw[i] + (char) raw[i + 1] + " )");
        }
        
        if(raw[i]=='/'){
            i = Name.setNameStringValue(pdfObject, i, raw, map, PDFkey, PDFkeyInt, objectReader);
        }else if(raw[i]=='f' && raw[i+1]=='a' && raw[i+2]=='l' && raw[i+3]=='s' && raw[i+4]=='e'){
            pdfObject.setBoolean(PDFkeyInt,false);
            i += 4;
        }else if(raw[i]=='t' && raw[i+1]=='r' && raw[i+2]=='u' && raw[i+3]=='e') {
            pdfObject.setBoolean(PDFkeyInt,true);
            i += 3;
        }else if(raw[i]=='(' || (raw[i]=='<' && raw[i-1]!='<' && raw[i+1]!='<')){
            i = TextStream.readTextStream(pdfObject, i, raw, PDFkeyInt, ignoreRecursion,objectReader);
        }else if(raw[i]=='['){
            i = setArray(pdfObject, i, raw, PDFkeyInt, objectReader);
        }else if((raw[i]=='<' && raw[i+1]=='<')){
            i = Dictionary.readDictionary(pdfObject, i, raw, PDFkeyInt, ignoreRecursion,objectReader);
        }else{
            i=General.readGeneral(pdfObject, i, raw, length, PDFkeyInt, map, ignoreRecursion, objectReader,PDFkey);
        }

        return i;
    }

    static int setArray(PdfObject pdfObject, int i, byte[] raw, int PDFkeyInt, PdfFileReader objectReader) {
        switch (PDFkeyInt) {
            
            case PdfDictionary.D:
            case PdfDictionary.OpenAction:
                 case PdfDictionary.K:
            case PdfDictionary.XFA:
                {
                    final ArrayDecoder objDecoder=new Array(objectReader, i, PdfDictionary.VALUE_IS_MIXED_ARRAY,raw);
                    i=objDecoder.readArray(pdfObject, PDFkeyInt);
                    break;
                }
            
            case PdfDictionary.Mask:           
                {
                    final ArrayDecoder objDecoder=new IntArray(objectReader, i, raw);
                    i=objDecoder.readArray(pdfObject, PDFkeyInt);
                    
                    break;
                }
            case PdfDictionary.C:
            case PdfDictionary.IC:
                {
                    final ArrayDecoder objDecoder=new FloatArray(objectReader, i, raw);
                    i=objDecoder.readArray(pdfObject, PDFkeyInt);
                    break;
                }
                
            case PdfDictionary.TR:    
            case PdfDictionary.OCGs:
                {
                    final ArrayDecoder objDecoder=new KeyArray(objectReader, i, raw);
                    i=objDecoder.readArray(pdfObject, PDFkeyInt);
                    break;
                }
           
            default:
                {
                    final ArrayDecoder objDecoder=new StringArray(objectReader, i, raw);
                    i=objDecoder.readArray(pdfObject, PDFkeyInt);
                    break;
                }
        }
        return i;
    }

    static int setTypeForDecodeParams(final int i, final byte[] raw, int pdfKeyType) {
        int ii=i;
        
        ii = StreamReaderUtils.skipSpaces(raw, ii);
        
        //see if might be object arrays
        if(raw[ii]!='<'){
            
            ii = StreamReaderUtils.skipSpacesOrOtherCharacter(raw, ii, 91);
            
            if(raw[ii]=='<' || (raw[ii]>='0' && raw[ii]<='9')) {
                pdfKeyType = PdfDictionary.VALUE_IS_OBJECT_ARRAY;
            }
            
        }
        return pdfKeyType;
    }

    /**
     * used by linearization to check object fully fully available and return false if not
     * @param pdfObject
     */
    public static synchronized boolean resolveFully(final PdfObject pdfObject, final PdfFileReader objectReader){
        
        boolean fullyResolved=pdfObject!=null;
        
        if(fullyResolved){
            
            final byte[] raw;
            if(pdfObject.getStatus()==PdfObject.DECODED) {
                raw = StringUtils.toBytes(pdfObject.getObjectRefAsString());
            } else {
                raw = pdfObject.getUnresolvedData();
            }
            
            //flag now done and flush raw data
            pdfObject.setStatus(PdfObject.DECODED);
            
            //allow for empty object
            if(raw[0]!='e' && raw[1]!='n' && raw[2]!='d' && raw[3]!='o' && raw[4]!='b' ){

                //allow for [ref] at top level (may be followed by gap
                int j=StreamReaderUtils.skipSpacesOrOtherCharacter(raw, 0, 91);

                // get object ref
                int keyStart = j;
                
                //move cursor to end of reference
                j = StreamReaderUtils.skipToEndOfRef(raw, j);

                final int ref = NumberUtils.parseInt(keyStart, j, raw);

                j=StreamReaderUtils.skipSpaces(raw, j);

                // get generation number
                keyStart = j;
                
                //move cursor to end of reference
                j = StreamReaderUtils.skipToEndOfRef(raw, j);

                final int generation = NumberUtils.parseInt(keyStart, j, raw);
                
                if(raw[raw.length-1]=='R') //recursively validate all child objects
                {
                    fullyResolved = resolveFullyChildren(pdfObject, fullyResolved, raw, ref, generation, objectReader);
                }
                
                if(fullyResolved){
                    pdfObject.ignoreRecursion(false);
                    final ObjectDecoder objDecoder=new ObjectDecoder(objectReader);
                    objDecoder.readDictionaryAsObject(pdfObject, j, raw);
                }
            }
        }
        
        return fullyResolved;
    }
    
    static boolean resolveFullyChildren(final PdfObject pdfObject, boolean fullyResolved, final byte[] raw, final int ref, final int generation, final PdfFileReader objectReader) {
        
        pdfObject.setRef(new String(raw));
        pdfObject.isDataExternal(true);
        
        final byte[] pageData = objectReader.readObjectAsByteArray(pdfObject, objectReader.isCompressed(ref, generation), ref, generation);
        
        //allow for data in Linear object not yet loaded
        if(pageData==null){
            pdfObject.setFullyResolved(false);
            fullyResolved=false;
        }else{
            pdfObject.setStatus(PdfObject.UNDECODED_DIRECT);
            pdfObject.setUnresolvedData(pageData, PdfDictionary.Linearized);
            pdfObject.isDataExternal(true);
            
            if(!resolveFully(pdfObject,objectReader)) {
                pdfObject.setFullyResolved(false);
            }
        }
        
        return fullyResolved;
    }
    
    /**
     * read object setup to contain only ref to data
     * @param pdfObject
     */
    public void checkResolved(final PdfObject pdfObject){
        
        if(pdfObject!=null && pdfObject.getStatus()!=PdfObject.DECODED){
            
            byte[] raw=pdfObject.getUnresolvedData();

            //flag now done and flush raw data
            pdfObject.setStatus(PdfObject.DECODED);
            
            //allow for empty object
            if(raw[0]=='e' && raw[1]=='n' && raw[2]=='d' && raw[3]=='o' && raw[4]=='b' ){
                //empty object
            }else if(StreamReaderUtils.isNull(raw,0)){
                //null object
            }else{ //we need to ref from ref elsewhere which may be indirect [ref], hence loop
                
                String objectRef=pdfObject.getObjectRefAsString();
                
                //allow for Color where starts [/ICCBased 2 0 R so we get the ref if present
                if(raw[0]=='['){
                    
                    //scan along to find number
                    int ptr=0;
                    final int len=raw.length;
                    for(int jj=0;jj<len;jj++){
                        
                        if(raw[jj]>='0' && raw[jj]<='9'){
                            ptr=jj;
                            jj=len;
                        }
                    }
                    
                    //check first non-number is R
                    int end=ptr;
                    while((raw[end]>='0' && raw[end]<='9') || raw[end]==' ' || raw[end]==10 || raw[end]==13 || raw[end]==9) {
                        end++;
                    }
                    //and store if it is a ref
                    if(raw[end]=='R') {
                        pdfObject.setRef(new String(raw, ptr, len - ptr));
                    }
                    
                }else if(raw[raw.length-1]=='R'){
                    objectRef=new String(raw);
                    pdfObject.setRef(objectRef);
                }else if(raw[0]!='<' && raw[raw.length-1]=='>'){
                    ////see case 23155 (encrypted annot needs obj ref appended so we can decrypt string later)
                    extractRefFromEnd(raw,pdfObject,objectReader);
                    return;
                }
                
                Dictionary.readDictionaryFromRefOrDirect(pdfObject,objectRef, 0, raw , -1,objectReader);
                
            }
        }
    }

    /**
     * see case 23155 (encrypted annot needs obj ref appended so we can decrypt string later)
     */
    private static void extractRefFromEnd(byte[] raw, final PdfObject pdfObject, PdfFileReader objectReader) {
        
        String objectRef;
        
        //scan along to find number
        int ptr=0;
        final int len=raw.length;
        for(int jj=0;jj<len;jj++){
            
            if(raw[jj]>='0' && raw[jj]<='9'){
                ptr=jj;
                jj=len;
            }
        }
        //check first non-number is R
        int end=ptr;
        while((raw[end]>='0' && raw[end]<='9') || raw[end]==' ' || raw[end]==10 || raw[end]==13 || raw[end]==9) {
            end++;
        }
        //and store if it is a ref
        if(raw[end]=='o' && raw[end+1]=='b' && raw[end+2]=='j') {
            objectRef=new String(raw, 0, end)+ 'R';
            int newArrayLen=raw.length-end-4;
            byte[] newArray=new byte[newArrayLen];
            System.arraycopy(raw, end+4, newArray, 0, newArrayLen);
            raw=newArray;
            pdfObject.setRef(objectRef);

            Dictionary.readDictionaryFromRefOrDirect(pdfObject,objectRef, 0, raw , -1,objectReader);
        }
    }

    static boolean isStringPair(final int i, final byte[] raw, boolean stringPair) {

        final int len=raw.length;
        for(int aa=i;aa<len;aa++){
            if(raw[aa]=='('){
                aa=len;
                stringPair =true;
            }else if(raw[aa]=='/' || raw[aa]=='>' || raw[aa]=='<' || raw[aa]=='[' || raw[aa]=='R'){
                aa=len;
            }else if(raw[aa]=='M' && raw[aa+1]=='C' && raw[aa+2]=='I' && raw[aa+3]=='D'){
                aa=len;
            }
        }
        return stringPair;
    }
    
    /**
     * set end if not end of data stream
     */
    public void setEndPt(final int dataPointer) {
        this.endPt=dataPointer;
    }
}
