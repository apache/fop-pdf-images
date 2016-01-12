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
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.fontbox.cff.CFFCIDFont;
import org.apache.fontbox.cff.CFFCharset;
import org.apache.fontbox.cff.CFFEncoding;
import org.apache.fontbox.cff.CFFFont;
import org.apache.fontbox.ttf.CmapSubtable;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;

import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType0;
import org.apache.pdfbox.pdmodel.font.PDCIDFontType2;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1CFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import org.apache.fop.fonts.FontInfo;
import org.apache.fop.fonts.SingleByteFont;
import org.apache.fop.fonts.Typeface;
import org.apache.fop.fonts.truetype.OTFSubSetFile;

import org.apache.fop.pdf.PDFText;


public class MergeFontsPDFWriter extends PDFWriter {
    protected static final Log log = LogFactory.getLog(MergeFontsPDFWriter.class);
    private COSDictionary fonts;
    private FontInfo fontInfo;
    private Typeface font;
    private FontContainer oldFont = null;
    protected Map<COSName, String> fontsToRemove = new HashMap<COSName, String>();
    private final Map<COSDictionary, FontContainer> fontMap = new HashMap<COSDictionary, FontContainer>();
    private static final Pattern SUBSET_PATTERN = Pattern.compile("[A-Z][A-Z][A-Z][A-Z][A-Z][A-Z]\\+.+");
    private Collection<String> parentFonts;

    public MergeFontsPDFWriter(COSDictionary fonts, FontInfo fontInfo, String key, List<COSName> resourceNames,
                               Collection<String> parentFonts, int mcid) {
        super(key, resourceNames, mcid);
        this.fonts = fonts;
        this.fontInfo = fontInfo;
        this.parentFonts = parentFonts;
    }

    public String writeText(PDStream pdStream) throws IOException {
        String txt = super.writeText(pdStream);
        if (fontsToRemove.isEmpty()) {
            return null;
        }
        for (COSName cn : fontsToRemove.keySet()) {
            fonts.removeItem(cn);
        }
        parentFonts.clear();
        parentFonts.addAll(fontsToRemove.values());
        return txt;
    }

    protected void readPDFArguments(Operator op, Collection<COSBase> arguments) throws IOException {
        for (COSBase c : arguments) {
            if (c instanceof COSName) {
                COSName cn = (COSName)c;
                COSDictionary fontData = (COSDictionary)fonts.getDictionaryObject(cn.getName());
                String internalName = fontsToRemove.get(cn);
                if (internalName == null && fontData != null) {
                    internalName = getNewFont(fontData, fontInfo, fontsToRemove.values());
                }
                if (fontData == null || internalName == null) {
                    s.append("/" + cn.getName());
                    addKey(cn);
                    if (op.getName().equals("Tf")) {
                        font = null;
                        oldFont = null;
                    }
                } else {
                    s.append("/" + internalName);
                    fontsToRemove.put(cn, internalName);
                    font = fontInfo.getUsedFonts().get(internalName);
                    oldFont = getFont(fontData);
                }
                s.append(" ");
            } else if (c instanceof COSString && font != null && ((FOPPDFFont)font).size() != 1) {
                List<String> word = readCOSString((COSString)c, oldFont);
                if (word == null) {
                    s.append(PDFText.escapeString(getString((COSString) c)));
                } else {
                    String x = getMappedWord(word, font, ((COSString) c).getBytes());
                    if (x == null) {
                        s.append(PDFText.escapeString(getString((COSString) c)));
                    } else {
                        s.append(x);
                    }
                }
            } else {
                processArg(op, c);
            }
        }
    }

    private String getNewFont(COSDictionary fontData, FontInfo fontinfo, Collection<String> usedFonts)
        throws IOException {
        String base = getUniqueFontName(fontData);
        if (base == null || usedFonts.contains(base) || (parentFonts != null && parentFonts.contains(base))) {
            return null;
        }
        try {
            for (Typeface t : fontinfo.getUsedFonts().values()) {
                if (t instanceof FOPPDFFont && base.equals(t.getFontName())) {
                    return ((FOPPDFFont)t).addFont(fontData);
                }
            }
            if (base.endsWith("cid") || fontData.getItem(COSName.SUBTYPE) != COSName.TYPE1
                    && fontData.getItem(COSName.SUBTYPE) != COSName.TRUE_TYPE) {
                fontinfo.addMetrics(base, new FOPPDFMultiByteFont(fontData, base));
            } else {
                fontinfo.addMetrics(base, new FOPPDFSingleByteFont(fontData, base));
            }
        } catch (IOException e) {
            log.warn(e.getMessage());
            return null;
        }
        fontinfo.useFont(base);
        return base;
    }

    private String getUniqueFontName(COSDictionary fontData) throws IOException {
        FontContainer fontContainer = getFont(fontData);
        PDFont font = fontContainer.font;
        if (font.getName() != null) {
            String extra = "";
            String name = getName(font.getName()) + "_" + ((COSName)fontData.getItem(COSName.SUBTYPE)).getName();
            if (font instanceof PDType0Font
                    && ((PDType0Font) font).getDescendantFont() instanceof PDCIDFontType0) {
                CFFFont cffFont = ((PDCIDFontType0) ((PDType0Font) font).getDescendantFont()).getCFFFont();
                if (cffFont instanceof CFFCIDFont
                        && ((CFFCIDFont)cffFont).getFdSelect().getClass().getName()
                        .equals("org.apache.fontbox.cff.CFFParser$Format0FDSelect")) {
                    extra += "format0";
                }
                return name + extra;
            } else if (font instanceof PDType0Font
                    && fontContainer.getToUnicode() != null
                    && ((PDType0Font) font).getDescendantFont() instanceof PDCIDFontType2) {
                if (!isSubsetFont(font.getName())) {
                    extra = "f3";
                }
                return name + extra;
            } else if (font instanceof PDTrueTypeFont && isSubsetFont(font.getName())) {
                TrueTypeFont tt = ((PDTrueTypeFont) font).getTrueTypeFont();
                for (CmapSubtable c : tt.getCmap().getCmaps()) {
                    if (c.getGlyphId(1) > 0) {
                        extra = "cid";
                    }
                }
                return name + extra;
            } else if (font instanceof PDType1CFont) {
                return getNamePDType1Font(name, (PDType1CFont) font);
            } else if (font instanceof PDType1Font) {
                return name;
            }
        }
        return null;
    }

    private String getNamePDType1Font(String name, PDType1CFont font) throws IOException {
        String extra = "";
        CFFEncoding encoding = font.getCFFType1Font().getEncoding();
        String eClass = encoding.getClass().getName();
        if (eClass.equals("org.apache.fontbox.cff.CFFParser$Format1Encoding")) {
            extra = "f1enc";
        } else if (eClass.equals("org.apache.fontbox.cff.CFFParser$Format0Encoding")) {
            extra = "f0enc";
        }
        CFFCharset cs = font.getCFFType1Font().getCharset();
        List<Integer> sids = MergeCFFFonts.getSids(cs);
        if (!sids.isEmpty() && sids.get(0) < OTFSubSetFile.NUM_STANDARD_STRINGS) {
            extra += "stdcs";
        }
        if (cs.getClass().getName().equals("org.apache.fontbox.cff.CFFParser$Format1Charset")) {
            extra += "f1cs";
        }
        return name + extra;
    }

    private String getString(COSString s) throws UnsupportedEncodingException {
        String encoding = "ISO-8859-1";
        byte[] data = s.getBytes();
        int start = 0;
        if (data.length > 2) {
            if (data[0] == (byte) 0xFF && data[1] == (byte) 0xFE) {
                encoding = "UTF-16LE";
                start = 2;
            } else if (data[0] == (byte) 0xFE && data[1] == (byte) 0xFF) {
                encoding = "UTF-16BE";
                start = 2;
            }
        }
        return new String(data, start, data.length - start, encoding);
    }

    private String getMappedWord(List<String> word, Typeface font, byte[] bytes) throws IOException {
        StringBuffer newOct = new StringBuffer();
        StringBuilder newHex = new StringBuilder();
        int i = 0;
        for (String str : word) {
            Integer mapped = getMapping(bytes[i]);
            if (mapped == null) {
                char c = str.charAt(0);
                if (str.length() > 1) {
                    c = (char) str.hashCode();
                }
                if (font.hasChar(c)) {
                    mapped = (int)font.mapChar(c);
                } else {
                    return null;
                }
            }
            newHex.append(String.format("%1$04x", mapped & 0xFFFF).toUpperCase(Locale.getDefault()));
            PDFText.escapeStringChar((char)mapped.intValue(), newOct);
            i++;
        }
        if (font instanceof SingleByteFont) {
            return "(" + newOct.toString() + ")";
        }
        return "<" + newHex.toString() + ">";
    }

    private Integer getMapping(byte i) throws IOException {
        if (oldFont.getEncoding() != null && font instanceof FOPPDFSingleByteFont) {
            String name = oldFont.getEncoding().getName(i);
            if (!name.equals(".notdef") && ((FOPPDFSingleByteFont)font).charMapGlobal.containsKey(name)) {
                return ((FOPPDFSingleByteFont)font).charMapGlobal.get(name);
            }
        }
        return null;
    }

    private List<String> readCOSString(COSString s, FontContainer oldFont) throws IOException {
        List<String> word = new ArrayList<String>();
        byte[] string = s.getBytes();
        InputStream in = new ByteArrayInputStream(string);
        while (in.available() > 0) {
            int code = oldFont.font.readCode(in);
            String unicode = oldFont.font.toUnicode(code);
            if (unicode == null) {
                return null;
            }
            word.add(unicode);
        }
        return word;
    }

    protected FontContainer getFont(COSDictionary fontData) throws IOException {
        if (!fontMap.containsKey(fontData)) {
            if (fontMap.size() > 10) {
                fontMap.clear();
            }
            fontMap.put(fontData, new FontContainer(fontData));
        }
        return fontMap.get(fontData);
    }

    private static boolean isSubsetFont(String s) {
        return SUBSET_PATTERN.matcher(s).matches();
    }

    protected static String getName(String name) {
        if (isSubsetFont(name)) {
            return name.split("\\+")[1].replace(" ", "");
        }
        return name.replace(" ", "");
    }
}
