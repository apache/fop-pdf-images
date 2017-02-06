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

package org.apache.fop.render.pdf;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.commons.io.IOUtils;
import org.apache.fontbox.cff.CFFParser;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.type1.Type1Font;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import org.apache.xmlgraphics.image.loader.ImageException;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.impl.ImageGraphics2D;
import org.apache.xmlgraphics.image.loader.util.SoftMapCache;
import org.apache.xmlgraphics.java2d.GeneralGraphics2DImagePainter;
import org.apache.xmlgraphics.java2d.GraphicContext;
import org.apache.xmlgraphics.ps.PSGenerator;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.fonts.CustomFont;
import org.apache.fop.fonts.FontInfo;
import org.apache.fop.fonts.FontType;
import org.apache.fop.fonts.MultiByteFont;
import org.apache.fop.fonts.Typeface;
import org.apache.fop.pdf.PDFAnnotList;
import org.apache.fop.pdf.PDFArray;
import org.apache.fop.pdf.PDFDictionary;
import org.apache.fop.pdf.PDFDocument;
import org.apache.fop.pdf.PDFFilterList;
import org.apache.fop.pdf.PDFGState;
import org.apache.fop.pdf.PDFPage;
import org.apache.fop.pdf.PDFResources;
import org.apache.fop.pdf.PDFStream;
import org.apache.fop.render.pdf.pdfbox.ImageConverterPDF2G2D;
import org.apache.fop.render.pdf.pdfbox.ImagePDF;
import org.apache.fop.render.pdf.pdfbox.PDFBoxAdapter;
import org.apache.fop.render.pdf.pdfbox.PDFBoxImageHandler;
import org.apache.fop.render.pdf.pdfbox.PSPDFGraphics2D;
import org.apache.fop.render.ps.PSDocumentHandler;
import org.apache.fop.render.ps.PSImageFormResource;
import org.apache.fop.render.ps.PSRenderingUtil;

import junit.framework.Assert;

public class PDFBoxAdapterTestCase {
    private Rectangle2D r = new Rectangle2D.Double();
    protected static final String CFF1 = "test/resources/2fonts.pdf";
    protected static final String CFF2 = "test/resources/2fonts2.pdf";
    protected static final String CFF3 = "test/resources/simpleh.pdf";
    protected static final String TTCID1 = "test/resources/ttcid1.pdf";
    protected static final String TTCID2 = "test/resources/ttcid2.pdf";
    protected static final String TTSubset1 = "test/resources/ttsubset.pdf";
    protected static final String TTSubset2 = "test/resources/ttsubset2.pdf";
    private static final String TTSubset3 = "test/resources/ttsubset3.pdf";
    private static final String TTSubset5 = "test/resources/ttsubset5.pdf";
    private static final String CFFCID1 = "test/resources/cffcid1.pdf";
    private static final String CFFCID2 = "test/resources/cffcid2.pdf";
    protected static final String Type1Subset1 = "test/resources/t1subset.pdf";
    protected static final String Type1Subset2 = "test/resources/t1subset2.pdf";
    private static final String Type1Subset3 = "test/resources/t1subset3.pdf";
    private static final String Type1Subset4 = "test/resources/t1subset4.pdf";
    protected static final String ROTATE = "test/resources/rotate.pdf";
    private static final String ANNOT = "test/resources/annot.pdf";
    private static final String SHADING = "test/resources/shading.pdf";
    private static final String LINK = "test/resources/link.pdf";
    private static final String IMAGE = "test/resources/image.pdf";
    private static final String HELLOTagged = "test/resources/taggedWorld.pdf";
    private static final String XFORM = "test/resources/xform.pdf";
    private static final String LOOP = "test/resources/loop.pdf";

    private PDFBoxAdapter getPDFBoxAdapter(boolean mergeFonts) {
        PDFDocument doc = new PDFDocument("");
        PDFPage pdfpage = new PDFPage(new PDFResources(doc), 0, r, r, r, r);
        doc.setMergeFontsEnabled(mergeFonts);
        pdfpage.setDocument(doc);
        pdfpage.setObjectNumber(1);
        return new PDFBoxAdapter(pdfpage, new HashMap(), new HashMap<Integer, PDFArray>());
    }

    @Test
    public void testPDFWriter() throws Exception {
        FontInfo fi = new FontInfo();
        String msg = writeText(fi, CFF3);
        Assert.assertTrue(msg, msg.contains("/Myriad_Pro"));
        Assert.assertEquals(fi.getUsedFonts().size(), 2);
        msg = writeText(fi, TTSubset1);
        Assert.assertTrue(msg, msg.contains("<74>-0.168 <65>-0.1523 <73>0.1528 <74>277.832"));
        msg = writeText(fi, TTSubset2);
        Assert.assertTrue(msg, msg.contains("(t)-0.168 (e)-0.1523 (s)0.1528 (t)"));
        msg = writeText(fi, TTSubset3);
        Assert.assertTrue(msg, msg.contains("[<0001>3 <0002>-7 <0003>] TJ"));
        msg = writeText(fi, TTSubset5);
        Assert.assertTrue(msg, msg.contains("[<0003>2 <0004>-7 <0007>] TJ"));
        msg = writeText(fi, TTCID1);
        Assert.assertTrue(msg, msg.contains("<0028003B0034003000420034>"));
        msg = writeText(fi, TTCID2);
        Assert.assertTrue(msg, msg.contains("<000F00100001002A0034003F00430034003C00310034004100010010000E000F0011>"));
        msg = writeText(fi, CFFCID1);
        Assert.assertTrue(msg, msg.contains("/Fm01700251251 Do"));
        msg = writeText(fi, CFFCID2);
        Assert.assertTrue(msg, msg.contains("/Fm01701174772 Do"));
        msg = writeText(fi, Type1Subset1);
        Assert.assertTrue(msg, msg.contains("/Verdana_Type1"));
        msg = writeText(fi, Type1Subset2);
        Assert.assertTrue(msg, msg.contains("[(2nd example)] TJ"));
        msg = writeText(fi, Type1Subset3);
        Assert.assertTrue(msg, msg.contains("/URWChanceryL-MediItal_Type1 20 Tf"));
        msg = writeText(fi, Type1Subset4);
        Assert.assertTrue(msg, msg.contains("/F15-1521012718 40 Tf"));

        for (Typeface font : fi.getUsedFonts().values()) {
            InputStream is = ((CustomFont) font).getInputStream();
            if (font.getFontType() == FontType.TYPE1C || font.getFontType() == FontType.CIDTYPE0) {
                byte[] data = IOUtils.toByteArray(is);
                CFFParser p = new CFFParser();
                p.parse(data).get(0);
            } else if (font.getFontType() == FontType.TRUETYPE) {
                TTFParser parser = new TTFParser();
                parser.parse(is);
            } else if (font.getFontType() == FontType.TYPE0) {
                TTFParser parser = new TTFParser(true);
                parser.parse(is);
            } else if (font.getFontType() == FontType.TYPE1) {
                Type1Font.createWithPFB(is);
            }
            Assert.assertTrue(((CustomFont) font).isEmbeddable());
            if (font instanceof MultiByteFont) {
                Assert.assertTrue(((MultiByteFont) font).getWidthsMap() != null);
            } else {
                Assert.assertFalse(((CustomFont)font).isSymbolicFont());
            }
        }
    }

    private String writeText(FontInfo fi, String pdf) throws IOException {
        PDDocument doc = PDDocument.load(new File(pdf));
        PDPage page = doc.getDocumentCatalog().getPages().get(0);
        AffineTransform at = new AffineTransform();
        String c = getPDFBoxAdapter(true).createStreamFromPDFBoxPage(doc, page, pdf, at, fi, new Rectangle());
//        PDResources sourcePageResources = page.findResources();
//        COSDictionary fonts = (COSDictionary)sourcePageResources.getCOSDictionary().getDictionaryObject(COSName.FONT);
//        PDFBoxAdapter.PDFWriter w = adapter. new MergeFontsPDFWriter(fonts, fi, "", new ArrayList<COSName>());
//        String c = w.writeText(page.getContents());
        doc.close();
        return c;
    }

    @Test
    public void testStream() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = new PDFPage(new PDFResources(pdfdoc), 0, r, r, r, r);
        pdfpage.setDocument(pdfdoc);
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDDocument doc = PDDocument.load(new File(ROTATE));
        PDPage page = doc.getDocumentCatalog().getPages().get(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        String stream = adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        Assert.assertEquals(at, new AffineTransform(-0.0, 1.0000000554888686, 1.0000000554888686, 0.0, 0.0,
                -2.0742416381835938E-5));
        Assert.assertTrue(stream.contains("/GS0106079 gs"));
        Assert.assertTrue(stream.contains("/TT0106079 1 Tf"));
        doc.close();
    }

    @Test
    public void testTaggedPDFWriter() throws IOException {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = new PDFPage(new PDFResources(pdfdoc), 0, r, r, r, r);
        pdfpage.setDocument(pdfdoc);
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), new HashMap<Integer, PDFArray>());
        adapter.setCurrentMCID(5);
        PDDocument doc = PDDocument.load(new File(HELLOTagged));
        PDPage page = doc.getDocumentCatalog().getPages().get(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        String stream = adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        Assert.assertTrue(stream, stream.contains("/P <</MCID 5 >>BDC"));
        doc.close();
    }

    @Test
    public void testAnnot() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = new PDFPage(new PDFResources(pdfdoc), 0, r, r, r, r);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), new HashMap<Integer, PDFArray>());
        PDDocument doc = PDDocument.load(new File(ANNOT));
        PDPage page = doc.getDocumentCatalog().getPages().get(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        pdfdoc.output(os);
        os.reset();
        adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        pdfdoc.outputTrailer(os);
        Assert.assertTrue(os.toString("UTF-8").contains("/Fields ["));
        doc.close();
    }

    @Test
    public void testAnnot2() throws Exception {
        PDFBoxAdapter adapter = getPDFBoxAdapter(false);
        PDDocument doc = PDDocument.load(new File(ANNOT));
        PDPage page = doc.getDocumentCatalog().getPages().get(0);
        COSArray annots = (COSArray) page.getCOSObject().getDictionaryObject(COSName.ANNOTS);
        COSDictionary dict = (COSDictionary) ((COSObject)annots.get(0)).getObject();
        dict.setItem(COSName.PARENT, COSInteger.ONE);

        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        doc.close();
    }

    @Test
    public void testLink() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = new PDFPage(new PDFResources(pdfdoc), 0, r, r, r, r);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        Map<Integer, PDFArray> pageNumbers = new HashMap<Integer, PDFArray>();
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), pageNumbers);
        PDDocument doc = PDDocument.load(new File(LINK));
        PDPage page = doc.getDocumentCatalog().getPages().get(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        String stream = adapter.createStreamFromPDFBoxPage(doc, page, "key", at, null, r);
        Assert.assertTrue(stream.contains("/Link <</MCID 5 >>BDC"));
        Assert.assertEquals(pageNumbers.size(), 4);
        PDFAnnotList annots = (PDFAnnotList) pdfpage.get("Annots");
        Assert.assertEquals(annots.toPDFString(), "[\n1 0 R\n2 0 R\n]");
        doc.close();
    }

    @Test
    public void testXform() throws Exception {
        PDFDocument pdfdoc = new PDFDocument("");
        pdfdoc.getFilterMap().put(PDFFilterList.DEFAULT_FILTER, Collections.singletonList("null"));
        pdfdoc.setMergeFontsEnabled(true);
        PDFPage pdfpage = new PDFPage(new PDFResources(pdfdoc), 0, r, r, r, r);
        pdfpage.setDocument(pdfdoc);
        pdfpage.setObjectNumber(1);
        Map<Integer, PDFArray> pageNumbers = new HashMap<Integer, PDFArray>();
        PDFBoxAdapter adapter = new PDFBoxAdapter(pdfpage, new HashMap(), pageNumbers);
        PDDocument doc = PDDocument.load(new File(XFORM));
        PDPage page = doc.getDocumentCatalog().getPages().get(0);
        AffineTransform at = new AffineTransform();
        Rectangle r = new Rectangle(0, 1650, 842000, 595000);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", at, new FontInfo(), r);
        doc.close();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pdfdoc.output(bos);
        Assert.assertFalse(bos.toString("UTF-8").contains("/W 5 /H 5 /BPC 8 /CS /RGB ID ÿÿÿ"));
    }

    @Test
    public void testPSPDFGraphics2D() throws Exception {
        ByteArrayOutputStream stream = pdfToPS(IMAGE);
        Assert.assertTrue(stream.toString("UTF-8"),
                stream.toString("UTF-8").contains("%%IncludeResource: form FOPForm:0\nFOPForm:0 execform"));

        pdfToPS(CFF1);
        pdfToPS(CFF2);
        pdfToPS(CFF3);
        pdfToPS(TTCID1);
        pdfToPS(TTCID2);
        pdfToPS(TTSubset1);
        pdfToPS(TTSubset2);
        pdfToPS(TTSubset3);
        pdfToPS(TTSubset5);
        pdfToPS(CFFCID1);
        pdfToPS(CFFCID2);
        pdfToPS(Type1Subset1);
        pdfToPS(Type1Subset2);
        pdfToPS(Type1Subset3);
        pdfToPS(Type1Subset4);
        pdfToPS(ROTATE);
        pdfToPS(LINK);
        pdfToPS(LOOP);
    }

    @Test
    public void testPDFToPDF() throws IOException {
        FontInfo fi = new FontInfo();
        writeText(fi, CFF1);
        writeText(fi, CFF2);
        writeText(fi, CFF3);
        writeText(fi, CFFCID1);
        writeText(fi, CFFCID2);
        writeText(fi, IMAGE);
        writeText(fi, LINK);
        writeText(fi, ROTATE);
        writeText(fi, SHADING);
        writeText(fi, TTCID1);
        writeText(fi, TTCID2);
        writeText(fi, TTSubset1);
        writeText(fi, TTSubset2);
        writeText(fi, TTSubset3);
        writeText(fi, TTSubset5);
        writeText(fi, Type1Subset1);
        writeText(fi, Type1Subset2);
        writeText(fi, Type1Subset3);
        writeText(fi, Type1Subset4);
        writeText(fi, LOOP);
    }

    private ByteArrayOutputStream pdfToPS(String pdf) throws IOException, ImageException {
        ImageConverterPDF2G2D i = new ImageConverterPDF2G2D();
        ImageInfo imgi = new ImageInfo("a", "b");
        PDDocument doc = PDDocument.load(new File(pdf));
        org.apache.xmlgraphics.image.loader.Image img = new ImagePDF(imgi, doc);
        ImageGraphics2D ig = (ImageGraphics2D)i.convert(img, null);
        GeneralGraphics2DImagePainter g = (GeneralGraphics2DImagePainter) ig.getGraphics2DImagePainter();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PSPDFGraphics2D g2d = (PSPDFGraphics2D) g.getGraphics(true, new FOPPSGeneratorImpl(stream));
        Rectangle2D rect = new Rectangle2D.Float(0, 0, 100, 100);
        GraphicContext gc = new GraphicContext();
        g2d.setGraphicContext(gc);
        ig.getGraphics2DImagePainter().paint(g2d, rect);
        doc.close();
        return stream;
    }

    static class FOPPSGeneratorImpl extends PSGenerator implements PSDocumentHandler.FOPPSGenerator {
        public FOPPSGeneratorImpl(OutputStream out) {
            super(out);
        }

        public PSDocumentHandler getHandler() {
            PSDocumentHandler handler = mock(PSDocumentHandler.class);
            PSRenderingUtil util = mock(PSRenderingUtil.class);
            when(util.isOptimizeResources()).thenReturn(true);
            when(handler.getPSUtil()).thenReturn(util);
            FOUserAgent mockedAgent = mock(FOUserAgent.class);
            when(handler.getUserAgent()).thenReturn(mockedAgent);
            when(mockedAgent.getTargetResolution()).thenReturn(72f);
            when(handler.getFormForImage(any(String.class))).thenReturn(new PSImageFormResource(0, ""));
            return handler;
        }

        public BufferedOutputStream getTempStream(URI uri) throws IOException {
            return new BufferedOutputStream(new ByteArrayOutputStream());
        }

        public Map<Integer, URI> getImages() {
            return new HashMap<Integer, URI>();
        }
    }

    @Test
    public void testPDFBoxImageHandler() throws Exception {
        ImageInfo imgi = new ImageInfo("a", "b");
        PDDocument doc = PDDocument.load(new File(SHADING));
        ImagePDF img = new ImagePDF(imgi, doc);
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = new PDFPage(new PDFResources(pdfdoc), 0, r, r, r, r);
        pdfpage.setDocument(pdfdoc);
        PDFGState g = new PDFGState();
        pdfdoc.assignObjectNumber(g);
        pdfpage.addGState(g);
        PDFContentGenerator con = new PDFContentGenerator(pdfdoc, null, null);
        FOUserAgent mockedAgent = mock(FOUserAgent.class);
        when(mockedAgent.isAccessibilityEnabled()).thenReturn(false);
        when(mockedAgent.getPDFObjectCache()).thenReturn(new SoftMapCache(true));
        PDFRenderingContext c = new PDFRenderingContext(mockedAgent, con, pdfpage, null);
        c.setPageNumbers(new HashMap<Integer, PDFArray>());
        new PDFBoxImageHandler().handleImage(c, img, new Rectangle());
        PDFResources res = c.getPage().getPDFResources();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        res.output(bos);
        Assert.assertTrue(bos.toString("UTF-8").contains("/ExtGState << /GS1"));
    }

    @Test
    public void testPDFCache() throws IOException {
        PDFDocument pdfdoc = new PDFDocument("");
        PDFPage pdfpage = new PDFPage(new PDFResources(pdfdoc), 0, r, r, r, r);
        pdfdoc.assignObjectNumber(pdfpage);
        pdfpage.setDocument(pdfdoc);
        Map<Object, Object> pdfCache = new HashMap<Object, Object>();
        Map<Object, Object> objectCachePerFile = new HashMap<Object, Object>();
        PDFBoxAdapter adapter = new PDFBoxAdapter(
                pdfpage, objectCachePerFile, new HashMap<Integer, PDFArray>(), pdfCache);
        PDDocument doc = PDDocument.load(new File(LOOP));
        PDPage page = doc.getDocumentCatalog().getPages().get(0);
        adapter.createStreamFromPDFBoxPage(doc, page, "key", new AffineTransform(), null, new Rectangle());
        doc.close();

        Object item = pdfCache.values().iterator().next();
        Assert.assertEquals(item.getClass(), PDFStream.class);
        item = pdfCache.keySet().iterator().next();
        Assert.assertEquals(item.getClass(), Integer.class);
        Assert.assertEquals(pdfCache.size(), 11);

        int pdfDictionary = 0;
        int strings = 0;
        for (Map.Entry<Object, Object> o : objectCachePerFile.entrySet()) {
            if (o.getValue().getClass().equals(PDFDictionary.class)) {
                pdfDictionary++;
            }
            if (o.getKey() instanceof String) {
                strings++;
            }
        }
        Assert.assertEquals(pdfDictionary, 26);
        Assert.assertEquals(strings, 34);
        Assert.assertEquals(objectCachePerFile.size(), 45);
    }
}
