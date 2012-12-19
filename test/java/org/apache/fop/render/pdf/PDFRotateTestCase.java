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

package org.apache.fop.render.pdf;

import java.awt.geom.Rectangle2D;

import junit.framework.Assert;

import org.apache.fop.render.pdf.pdfbox.ImageConverterPDF2G2D;
import org.apache.fop.render.pdf.pdfbox.ImagePDF;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.xmlgraphics.image.loader.Image;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.impl.ImageGraphics2D;
import org.apache.xmlgraphics.java2d.GraphicContext;
import org.apache.xmlgraphics.java2d.ps.PSGraphics2D;
import org.junit.Test;

public class PDFRotateTestCase {
    
    @Test
    public void test() throws Exception {    	
        ImageConverterPDF2G2D i = new ImageConverterPDF2G2D();
        ImageInfo imgi = new ImageInfo("a", "b");
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage();
        page.setRotation(90);
        doc.addPage(page);
        Image img = new ImagePDF(imgi, doc);
        ImageGraphics2D ig = (ImageGraphics2D)i.convert(img, null);
        Rectangle2D rect = new Rectangle2D.Float(0, 0, 100, 100);
        
        PSGraphics2D g2d = new PSGraphics2D(true);
        GraphicContext gc = new GraphicContext();
        g2d.setGraphicContext(gc);
        ig.getGraphics2DImagePainter().paint(g2d, rect);
        Assert.assertTrue(g2d.getTransform().getShearX() == -0.12626262626262627);
    }
}