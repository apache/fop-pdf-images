/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.fop.render.pdf.pdfbox;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.fontbox.cmap.CMap;
import org.apache.fontbox.ttf.CMAPEncodingEntry;
import org.apache.fontbox.ttf.TrueTypeFont;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.encoding.DictionaryEncoding;
import org.apache.pdfbox.encoding.Encoding;
import org.apache.pdfbox.encoding.EncodingManager;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptorDictionary;
import org.apache.pdfbox.pdmodel.font.PDFontFactory;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import org.apache.fop.fonts.EmbeddingMode;
import org.apache.fop.fonts.FontType;
import org.apache.fop.fonts.SingleByteEncoding;
import org.apache.fop.fonts.SingleByteFont;
import org.apache.fop.fonts.truetype.FontFileReader;
import org.apache.fop.pdf.PDFDictionary;

public class FOPPDFSingleByteFont extends SingleByteFont implements FOPPDFFont {
    private int fontCount;
    private PDFont font;
    protected PDFDictionary ref;
    protected Map<String, Integer> charMapGlobal = new LinkedHashMap<String, Integer>();
    private Map<Integer, Integer> newWidth = new HashMap<Integer, Integer>();
    private Map<String, byte[]> charStringsDict;
    private MergeTTFonts.Cmap newCmap = new MergeTTFonts.Cmap();
    private Map<Integer, String> encodingMap = new TreeMap<Integer, String>();
    private int encodingSkip;
    private MergeTTFonts mergeTTFonts = new MergeTTFonts();
    private MergeCFFFonts mergeCFFFonts = new MergeCFFFonts();
    private MergeType1Fonts mergeType1Fonts = new MergeType1Fonts();
    private String shortFontName;
    private final Map<COSDictionary, PDFont> fontMap = new HashMap<COSDictionary, PDFont>();

    public FOPPDFSingleByteFont(COSDictionary fontData, String name) throws IOException {
        super(null, EmbeddingMode.FULL);
        if (fontData.getItem(COSName.SUBTYPE) == COSName.TRUE_TYPE) {
            setFontType(FontType.TRUETYPE);
        }
        width = new int[0];
        font = getFont(fontData);
        setFirstChar(font.getFirstChar());
        setLastChar(font.getLastChar());
        shortFontName = MergeFontsPDFWriter.getName(font.getBaseFont());
        loadFontFile(font);
        float[] bBoxF = font.getFontBoundingBox().getCOSArray().toFloatArray();
        int[] bBox = new int[bBoxF.length];
        for (int i = 0; i < bBox.length; i++) {
            bBox[i] = (int)bBoxF[i];
        }
        setFontBBox(bBox);

        setFontName(name);
        Object cmap = getCmap(font);
        for (int i = font.getFirstChar(); i <= font.getLastChar(); i++) {
            String mappedChar = getChar(cmap, i);
            if (mappedChar != null && !charMapGlobal.containsKey(mappedChar)) {
                charMapGlobal.put(mappedChar, i);
            }
        }
        //mark font as used
        notifyMapOperation();
        FOPPDFMultiByteFont.setProperties(this, font);
        if (font.getWidths() != null) {
            //if width contains 0 we cant rely on codeToNameMap
            boolean usesZero = font.getWidths().contains(0);
            Set<Integer> codeToName = getCodeToName(font.getFontEncoding()).keySet();
            for (int i = getFirstChar();
                 i <= Math.min(getLastChar(), getFirstChar() + font.getWidths().size()); i++) {
                if (usesZero || codeToName.contains(i)) {
                    int w = font.getWidths().get(i - getFirstChar());
                    newWidth.put(i, w);
                } else {
                    newWidth.put(i, 0);
                }
            }
        }
        mapping = new FOPPDFEncoding();
        encodingSkip = font.getLastChar() + 1;
        addEncoding(font);
    }

    private Map<Integer, String> getCodeToName(Encoding encoding) {
        Map<Integer, String> codeToName = new HashMap<Integer, String>();
        if (encoding != null) {
            COSBase cos = encoding.getCOSObject();
            if (cos instanceof COSDictionary) {
                COSDictionary enc = (COSDictionary) cos;
                COSName baseEncodingName = (COSName) enc.getDictionaryObject(COSName.BASE_ENCODING);
                if (baseEncodingName != null) {
                    try {
                        Encoding baseEncoding = EncodingManager.INSTANCE.getEncoding(baseEncodingName);
                        codeToName.putAll(baseEncoding.getCodeToNameMap());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                COSArray differences = (COSArray)enc.getDictionaryObject(COSName.DIFFERENCES);
                int currentIndex = -1;
                for (int i = 0; differences != null && i < differences.size(); i++) {
                    COSBase next = differences.getObject(i);
                    if (next instanceof COSNumber) {
                        currentIndex = ((COSNumber)next).intValue();
                    } else if (next instanceof COSName) {
                        COSName name = (COSName)next;
                        codeToName.put(currentIndex++, name.getName());
                    }
                }
            } else {
                return encoding.getCodeToNameMap();
            }
        }
        return codeToName;
    }

    private Object getCmap(PDFont font) throws IOException {
        if (font.getFontEncoding() != null) {
            return font.getFontEncoding();
        }
        return font.getToUnicodeCMap();
    }

    private PDStream readFontFile(PDFont font) throws IOException {
        PDFontDescriptorDictionary fd = (PDFontDescriptorDictionary) font.getFontDescriptor();
        setFlags(fd.getFlags());
        PDStream ff = fd.getFontFile3();
        if (ff == null) {
            ff = fd.getFontFile2();
            if (ff == null) {
                ff = fd.getFontFile();
            }
        } else {
            setFontType(FontType.TYPE1C);
        }
        if (ff == null) {
            throw new IOException(font.getBaseFont() + " no font file");
        }
        return ff;
    }

    private void loadFontFile(PDFont font) throws IOException {
        PDStream ff = readFontFile(font);
        mergeFontFile(ff.createInputStream(), font);
        if (font instanceof PDTrueTypeFont) {
            TrueTypeFont ttfont = ((PDTrueTypeFont) font).getTTFFont();
            CMAPEncodingEntry[] cmapList = ttfont.getCMAP().getCmaps();
            for (CMAPEncodingEntry c : cmapList) {
                newCmap.platformId = c.getPlatformId();
                newCmap.platformEncodingId = c.getPlatformEncodingId();
                for (int i = 0; i < 256 * 256; i++) {
                    if (c.getGlyphId(i) != 0) {
                        newCmap.glyphIdToCharacterCode.put(i, c.getGlyphId(i));
                    }
                }
            }
            FOPPDFMultiByteFont.mergeMaxp(ttfont, mergeTTFonts.maxp);
        }
    }

    @Override
    public boolean hasChar(char c) {
        return charMapGlobal.containsKey(String.valueOf(c));
    }

    @Override
    public char mapChar(char c) {
        return mapping.mapChar(c);
    }

    public String getEmbedFontName() {
        return shortFontName;
    }

    public int[] getWidths() {
        width = new int[getLastChar() - getFirstChar() + 1];
        for (int i = getFirstChar(); i <= getLastChar(); i++) {
            if (newWidth.containsKey(i)) {
                width[i - getFirstChar()] = newWidth.get(i);
            } else {
                width[i - getFirstChar()] = 0;
            }
        }
        return width.clone();
    }

    public String addFont(COSDictionary fontData) throws IOException {
        PDFont font = getFont(fontData);
        if (font instanceof PDType1Font && differentGlyphData((PDType1Font) font)) {
            return null;
        }
        mergeWidths(font);
        if (font.getFirstChar() < getFirstChar()) {
            setFirstChar(font.getFirstChar());
        }
        for (int w : newWidth.keySet()) {
            if (w > getLastChar()) {
                setLastChar(w);
            }
        }
        loadFontFile(font);
        addEncoding(font);
        return getFontName();
    }

    public int size() {
        return fontCount;
    }

    private Map<String, byte[]> getCharStringsDict(PDType1Font font) throws IOException {
        if (getFontType() == FontType.TYPE1) {
            return font.getType1Font().getCharStringsDict();
        }
        return font.getType1CFont().getCFFFont().getCharStringsDict();
    }

    private boolean differentGlyphData(PDType1Font otherFont) throws IOException {
        if (charStringsDict == null) {
            charStringsDict = getCharStringsDict((PDType1Font) font);
        }
        for (Map.Entry<String, byte[]> s : getCharStringsDict(otherFont).entrySet()) {
            if (charStringsDict.containsKey(s.getKey())) {
                int numberDiff = 0;
                byte[] b1 = charStringsDict.get(s.getKey());
                byte[] b2 = s.getValue();
                int b1Index = b1.length - 1;
                int b2Index = b2.length - 1;
                while (b1Index >= 0 && b2Index >= 0) {
                    if (b1[b1Index] != b2[b2Index]) {
                        numberDiff++;
                        if (numberDiff > 2) {
                            break;
                        }
                    }
                    b1Index--;
                    b2Index--;
                }
                if (numberDiff > 2) {
//                        log.info(getFontName() + " " + s.getKey() + " not equal " + numberdiff);
                    return true;
                }
            }
        }
        return false;
    }

    private void mergeWidths(PDFont font) throws IOException {
        int w = 0;
        int skipGlyphIndex = getLastChar() + 1;
        Object cmap = getCmap(font);
        Set<Integer> codeToName = getCodeToName(font.getFontEncoding()).keySet();
        for (int i = font.getFirstChar(); i <= font.getLastChar(); i++) {
            boolean addedWidth = false;
            int glyphIndexPos = skipGlyphIndex;
            if (font instanceof PDTrueTypeFont) {
                glyphIndexPos = i;
            }
            int neww = 0;
            if (font.getWidths() != null) {
                neww = font.getWidths().get(i - font.getFirstChar());
                if (!newWidth.containsKey(i) || newWidth.get(i) == 0) {
                    if (getFontType() == FontType.TYPE1
                            || font instanceof PDTrueTypeFont
                            || codeToName.contains(i)) {
                        newWidth.put(i, neww);
                        glyphIndexPos = i;
                    } else {
                        newWidth.put(i, 0);
                    }
                    addedWidth = true;
                }
            }
            String mappedChar = getChar(cmap, i);
            if (mappedChar != null && !charMapGlobal.containsKey(mappedChar)) {
                charMapGlobal.put(mappedChar, glyphIndexPos);
                if (!addedWidth && w < font.getWidths().size()) {
                    newWidth.put(newWidth.size() + getFirstChar(), neww);
                }
                skipGlyphIndex++;
            }
            w++;
        }
    }

    private String getChar(Object cmap, int i) throws IOException {
        if (cmap instanceof CMap) {
            CMap c = (CMap)cmap;
            int size = 1;
            if (c.hasTwoByteMappings()) {
                size = 2;
            }
            return c.lookup(i, size);
        }
        Encoding enc = (Encoding)cmap;
        if (enc instanceof DictionaryEncoding) {
            return enc.getName(i);
        }
        return enc.getCharacter(i);
    }

    public String getEncodingName() {
        if (font.getFontEncoding() != null) {
            COSBase cosObject = font.getFontEncoding().getCOSObject();
            if (cosObject != null) {
                if (cosObject instanceof COSDictionary) {
                    COSBase item = ((COSDictionary) cosObject).getItem(COSName.BASE_ENCODING);
                    if (item != null) {
                        return ((COSName)item).getName();
                    }
                } else if (cosObject instanceof COSName) {
                    return ((COSName) cosObject).getName();
                } else {
                    throw new RuntimeException(cosObject.toString() + " not supported");
                }
            }
        }
        return null;
    }

    private void addEncoding(PDFont fontForEnc) {
        List<String> added = new ArrayList<String>(encodingMap.values());
        Map<Integer, String> codeToName = getCodeToName(fontForEnc.getFontEncoding());
        for (int i = fontForEnc.getFirstChar(); i <= fontForEnc.getLastChar(); i++) {
            if (codeToName.keySet().contains(i)) {
                String s = codeToName.get(i);
                if (!added.contains(s)) {
                    if (!encodingMap.containsKey(i)) {
                        encodingMap.put(i, s);
                    } else {
                        encodingMap.put(encodingSkip, s);
                        encodingSkip++;
                    }
                }
            }
        }
    }

    class FOPPDFEncoding implements SingleByteEncoding {
        private boolean cmap;

        public String getName() {
            return "FOPPDFEncoding";
        }

        public char mapChar(char c) {
            if (charMapGlobal.containsKey(String.valueOf(c))) {
                return (char)charMapGlobal.get(String.valueOf(c)).intValue();
            }
            return 0;
        }

        public String[] getCharNameMap() {
            Collection<String> v = encodingMap.values();
            return v.toArray(new String[v.size()]);
        }

        public char[] getUnicodeCharMap() {
            if (cmap) {
                if (MergeFontsPDFWriter.getToUnicode(font) == null) {
                    return new char[0];
                }
                List<String> cmapStrings = new ArrayList<String>();
                Map<Integer, String> cm = new HashMap<Integer, String>();
                for (Map.Entry<String, Integer> o : charMapGlobal.entrySet()) {
                    cm.put(o.getValue(), o.getKey());
                }
                for (int i = 0; i < getLastChar() + 1; i++) {
                    if (cm.containsKey(i)) {
                        cmapStrings.add(cm.get(i));
                    } else {
                        cmapStrings.add(" ");
                    }
                }
                return fromStringToCharArray(cmapStrings);
            }
            cmap = true;
            return toCharArray(encodingMap.keySet());
        }

        private char[] fromStringToCharArray(Collection<String> list) {
            char[] ret = new char[list.size()];
            int i = 0;
            for (String e : list) {
                if (e.length() > 0) {
                    ret[i++] = e.charAt(0);
                }
            }
            return ret;
        }

        private char[] toCharArray(Collection<Integer> list) {
            char[] ret = new char[list.size()];
            int i = 0;
            for (int e : list) {
                ret[i++] = (char)e;
            }
            return ret;
        }
    }

    public PDFDictionary getRef() {
        return ref;
    }

    public void setRef(PDFDictionary d) {
        ref = d;
    }

    public boolean isEmbeddable() {
        return true;
    }

    public boolean isSymbolicFont() {
        return false;
    }

    private void mergeFontFile(InputStream ff, PDFont pdFont) throws IOException {
        if (getFontType() == FontType.TRUETYPE) {
            Map<Integer, Integer> chars = new HashMap<Integer, Integer>();
            chars.put(0, 0);
            mergeTTFonts.readFont(new FontFileReader(ff), chars, false);
        } else if (getFontType() == FontType.TYPE1) {
            mergeType1Fonts.readFont(ff, (PDType1Font) pdFont);
        } else {
            mergeCFFFonts.readType1CFont(ff, shortFontName);
        }
        fontCount++;
    }

    public InputStream getInputStream() throws IOException {
        if (getFontType() == FontType.TYPE1C) {
            mergeCFFFonts.writeFont();
            return new ByteArrayInputStream(mergeCFFFonts.getFontSubset());
        }
        if (getFontType() == FontType.TRUETYPE) {
            mergeTTFonts.writeFont(newCmap);
            return new ByteArrayInputStream(mergeTTFonts.getFontSubset());
        }
        if (getFontType() == FontType.TYPE1) {
            return new ByteArrayInputStream(mergeType1Fonts.writeFont());
        }
        return null;
    }

    protected PDFont getFont(COSDictionary fontData) throws IOException {
        if (!fontMap.containsKey(fontData)) {
            if (fontMap.size() > 10) {
                fontMap.clear();
            }
            fontMap.put(fontData, PDFontFactory.createFont(fontData));
        }
        return fontMap.get(fontData);
    }
}