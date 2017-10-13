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

/* $Id$ */

package org.apache.fop.render.pdf.pdfbox;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fontbox.FontBoxFont;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.CIDFontMapping;
import org.apache.pdfbox.pdmodel.font.FontMapper;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.PDCIDSystemInfo;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShading;
import org.apache.pdfbox.rendering.PDFRenderer;

import org.apache.xmlgraphics.image.loader.Image;
import org.apache.xmlgraphics.image.loader.ImageException;
import org.apache.xmlgraphics.image.loader.ImageFlavor;
import org.apache.xmlgraphics.image.loader.impl.AbstractImageConverter;
import org.apache.xmlgraphics.image.loader.impl.ImageGraphics2D;
import org.apache.xmlgraphics.image.loader.util.ImageUtil;
import org.apache.xmlgraphics.java2d.GeneralGraphics2DImagePainter;
import org.apache.xmlgraphics.java2d.Graphics2DImagePainter;
import org.apache.xmlgraphics.java2d.ps.PSGraphics2D;
import org.apache.xmlgraphics.ps.PSGenerator;

import org.apache.fop.fonts.CustomFont;
import org.apache.fop.fonts.LazyFont;
import org.apache.fop.fonts.MultiByteFont;
import org.apache.fop.fonts.Typeface;
import org.apache.fop.render.java2d.CustomFontMetricsMapper;

/**
 * Image converter implementation to convert PDF pages into Java2D images.
 */
public class ImageConverterPDF2G2D extends AbstractImageConverter {
    private static final Log LOG = LogFactory.getLog(ImageConverterPDF2G2D.class);

    /** {@inheritDoc} */
    public Image convert(Image src, Map hints) throws ImageException,
            IOException {
        float dpi = 72;
        if (hints != null) {
            dpi = (Float)hints.get("SOURCE_RESOLUTION");
            if (dpi == 72) {
                //note we are doing twice as many pixels because
                //the default size is not really good resolution,
                //so create an image that is twice the size
                dpi *= 2;
            }
        }
        checkSourceFlavor(src);
        assert src instanceof ImagePDF;
        ImagePDF imgPDF = (ImagePDF)src;

        final int selectedPage = ImageUtil.needPageIndexFromURI(
                src.getInfo().getOriginalURI());

        PDDocument pddoc = imgPDF.getPDDocument();

        Graphics2DImagePainter painter =
                new Graphics2DImagePainterPDF(pddoc, dpi, selectedPage, imgPDF.getInfo().getOriginalURI());

        ImageGraphics2D g2dImage = new ImageGraphics2D(src.getInfo(), painter);
        return g2dImage;
    }

    /** {@inheritDoc} */
    public ImageFlavor getSourceFlavor() {
        return ImagePDF.PDFBOX_IMAGE;
    }

    /** {@inheritDoc} */
    public ImageFlavor getTargetFlavor() {
        return ImageFlavor.GRAPHICS2D;
    }

    /** {@inheritDoc} */
    @Override
    public int getConversionPenalty() {
        return 1000; //Use only if no native embedding is possible
    }

    private static class Graphics2DImagePainterPDF implements GeneralGraphics2DImagePainter {

        private final PDPage page;
        private final PDDocument pdDocument;
        private float dpi;
        private int selectedPage;
        private FopFontProvider fopFontProvider = new FopFontProvider();
        private String uri;

        public Graphics2DImagePainterPDF(PDDocument pddoc, float dpi, int selectedPage, String uri) {
            this.dpi = dpi;
            pdDocument = pddoc;
            this.selectedPage = selectedPage;
            page = pdDocument.getPage(selectedPage);
            this.uri = uri;
        }

        /** {@inheritDoc} */
        public Dimension getImageSize() {
            PDRectangle mediaBox = page.getMediaBox();
            int wmpt = (int)Math.ceil(mediaBox.getWidth() * 1000);
            int hmpt = (int)Math.ceil(mediaBox.getHeight() * 1000);
            return new Dimension(wmpt, hmpt);
        }

        /** {@inheritDoc} */
        public void paint(Graphics2D g2d, Rectangle2D area) {
            fopFontProvider.start();
            try {
                PDRectangle mediaBox = page.getCropBox();
                AffineTransform at = new AffineTransform();
                int rotation = page.getRotation();
                if (rotation == 90 || rotation == 270) {
                    at.scale(area.getWidth() / area.getHeight(), area.getHeight() / area.getWidth());
                }
                if (g2d instanceof PSGraphics2D && new PageUtil().pageHasTransparency(page.getResources())) {
                    drawPageAsImage(at, g2d);
                } else {
                    at.translate(area.getX(), area.getY());
                    at.scale(area.getWidth() / mediaBox.getWidth(),
                            area.getHeight() / mediaBox.getHeight());
                    g2d.transform(at);
                    new PDFRenderer(pdDocument).renderPageToGraphics(selectedPage, g2d);
                }
            } catch (UnsupportedOperationException e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException("Error while painting PDF page: " + uri + " " + t.getMessage(), t);
            } finally {
                fopFontProvider.close();
            }
        }

        private void drawPageAsImage(AffineTransform at, Graphics2D g2d) throws IOException {
            PDFRenderer renderer = new PDFRenderer(pdDocument);
            BufferedImage bi = renderer.renderImageWithDPI(selectedPage, dpi);
            at.scale(72 / dpi, 72 / dpi);
            g2d.drawImage(bi, at, null);
        }

        static class PageUtil {
            private List<COSDictionary> visited = new ArrayList<COSDictionary>();

            private boolean pageHasTransparency(PDResources res) throws IOException {
                if (res != null) {
                    visited.add(res.getCOSObject());
                    if (res.getShadingNames() != null) {
                        for (COSName name : res.getShadingNames()) {
                            PDShading s = res.getShading(name);
                            if ((s.getShadingType() != 2 && s.getShadingType() != 3)
                                    || (s.getShadingType() == 3 && s.getFunction().getFunctionType() == 2)
                                    || (s.getShadingType() == 2
                                    && s.getColorSpace().toString().contains("FunctionType"))) {
                                LOG.warn(s.getClass().getName() + " not supported converting to image");
                                return true;
                            }
//                        if (s.getShadingType() == 3) {
//                            COSArray sourceFunctions = ((PDFunctionType3)s.getFunction()).getFunctions();
//                            for (COSBase sf : sourceFunctions) {
//                                PDFunction f = PDFunction.create(sf);
//                                if (f.getFunctionType() == 2) {
//                                    LOG.warn(s.getClass().getName() + " not supported converting to image");
//                                    return true;
//                                }
//                            }
//                        }
                        }
                    }
                    for (COSName pdxObjectName : res.getXObjectNames()) {
                        PDXObject pdxObject = res.getXObject(pdxObjectName);
                        if (pdxObject instanceof PDFormXObject) {
                            PDFormXObject form = (PDFormXObject) pdxObject;
                            if (form.getGroup() != null && COSName.TRANSPARENCY.equals(
                                    form.getGroup().getCOSObject().getDictionaryObject(COSName.S))) {
                                return true;
                            }
                            PDResources formRes = form.getResources();
                            if (formRes != null && !visited.contains(formRes.getCOSObject())
                                    && pageHasTransparency(formRes)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        }

        public Graphics2D getGraphics(boolean textAsShapes, PSGenerator gen) {
            PSPDFGraphics2D graphics = new PSPDFGraphics2D(textAsShapes, gen);
            return graphics;
        }

        public void addFallbackFont(String s, Object font) {
            fopFontProvider.fonts.put(s, font);
        }
    }

    static class FopFontProvider {
        private static FopFontMapper fopFontMapper = new FopFontMapper();
        static {
            FontMappers.set(fopFontMapper);
        }
        private Map<String, Object> fonts = new HashMap<String, Object>();
        private Map<String, TrueTypeFont> ttFonts = new HashMap<String, TrueTypeFont>();

        void start() {
            fopFontMapper.fopFontProvider.set(this);
        }

        void close() {
            fopFontMapper.fopFontProvider.remove();
        }

        private CustomFont getFont(String name) throws IOException {
            Object typeface = fonts.get(name);
            if (typeface instanceof LazyFont) {
                Typeface rf = ((LazyFont) typeface).getRealFont();
                return (CustomFont) rf;
            } else if (typeface instanceof CustomFontMetricsMapper) {
                Typeface rf = ((CustomFontMetricsMapper) typeface).getRealFont();
                return (CustomFont) rf;
            }
            return null;
        }

        public TrueTypeFont getTrueTypeFont(String postScriptName) {
            if (!ttFonts.containsKey(postScriptName)) {
                try {
                    CustomFont font = getFont(postScriptName);
                    if (font instanceof MultiByteFont && !((MultiByteFont)font).isOTFFile()) {
                        TTFParser ttfParser = new TTFParser(false, true);
                        TrueTypeFont ttf = ttfParser.parse(font.getInputStream());
                        ttFonts.put(postScriptName, ttf);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return ttFonts.get(postScriptName);
        }
    }


    static class FopFontMapper implements FontMapper {
        private FontMapper defaultFontMapper;
        private ThreadLocal<FopFontProvider> fopFontProvider = new ThreadLocal<FopFontProvider>();

        FopFontMapper() {
            defaultFontMapper = FontMappers.instance();
        }

        private TrueTypeFont getTrueTypeFont(String baseFont) {
            FopFontProvider fontProvider = fopFontProvider.get();
            if (fontProvider == null) {
                return null;
            }
            return fontProvider.getTrueTypeFont(baseFont);
        }

        public FontMapping<TrueTypeFont> getTrueTypeFont(String baseFont, PDFontDescriptor fontDescriptor) {
            TrueTypeFont fopFont = getTrueTypeFont(baseFont);
            if (fopFont != null) {
                return new FontMapping<TrueTypeFont>(fopFont, true);
            }
            return defaultFontMapper.getTrueTypeFont(baseFont, fontDescriptor);
        }


        public FontMapping<FontBoxFont> getFontBoxFont(String baseFont, PDFontDescriptor fontDescriptor) {
            TrueTypeFont fopFont = getTrueTypeFont(baseFont);
            if (fopFont != null) {
                return new FontMapping<FontBoxFont>(fopFont, true);
            }
            return defaultFontMapper.getFontBoxFont(baseFont, fontDescriptor);
        }


        public CIDFontMapping getCIDFont(String baseFont, PDFontDescriptor fontDescriptor,
                                         PDCIDSystemInfo cidSystemInfo) {
            return defaultFontMapper.getCIDFont(baseFont, fontDescriptor, cidSystemInfo);
        }
    }

}
