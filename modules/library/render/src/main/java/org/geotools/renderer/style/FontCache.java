/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 * 
 *    (C) 2002-2008, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.renderer.style;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lookup and caches font definitions for faster retrieval
 * 
 * @author Andrea Aime - TOPP
 *
 * @source $URL$
 */
public class FontCache {
    /** The logger for the rendering module. */
    private static final Logger LOGGER = org.geotools.util.logging.Logging
            .getLogger("org.geotools.rendering");

    static FontCache defaultInstance;

    /** Set containing the font families known of this machine */
    Set<String> systemFonts = new HashSet<String>();

    /** Fonts already loaded */
    Map<String, Font> loadedFonts = new ConcurrentHashMap<String, Font>();

    /**
     * Returns the default, system wide font cache
     * 
     * @deprecated Use {@link #getDefaultInstance()} instead
     */
    public static FontCache getDefaultInsance() {
        return getDefaultInstance();
    }

    /**
     * Returns the default, system wide font cache
     * 
     * @since 2.6
     */
    public static FontCache getDefaultInstance() {
        if (defaultInstance == null) {
            defaultInstance = new FontCache();
        }
        return defaultInstance;
    }

    public synchronized Font getFont(String requestedFont) {
        // see if the font has already been loaded
        java.awt.Font javaFont = null;
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("trying to load " + requestedFont);
        }

        if (loadedFonts.containsKey(requestedFont)) {
            return loadedFonts.get(requestedFont);
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("not already loaded");
        }

        // if not, try to load from the java runtime or as an URL
        if (getSystemFonts().contains(requestedFont)) {
            javaFont = new java.awt.Font(requestedFont, Font.PLAIN, 12);
        } else {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("not a system font");
            }

            javaFont = loadFromUrl(requestedFont);
        }

        // log the result and exit
        if (javaFont == null) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.fine("Could not load font " + requestedFont);
            }
        } else {
            loadedFonts.put(requestedFont, javaFont);
        }
        return javaFont;
    }

    /**
     * Tries to load the specified font name as a URL
     * 
     * @param fontUrl
     * @return
     */
    java.awt.Font loadFromUrl(String fontUrl) {
        // may be its a file or url
        InputStream is = null;

        if (fontUrl.startsWith("http") || fontUrl.startsWith("file:")) {
            try {
                URL url = new URL(fontUrl);
                is = url.openStream();
            } catch (MalformedURLException mue) {
                // this may be ok - but we should mention it
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("Bad url in SLDStyleFactory " + fontUrl + "\n" + mue);
                }
            } catch (IOException ioe) {
                // we'll ignore this for the moment
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("IO error in SLDStyleFactory " + fontUrl + "\n" + ioe);
                }
            }
        } else {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("not a URL");
            }

            File file = new File(fontUrl);

            try {
                is = new FileInputStream(file);
            } catch (FileNotFoundException fne) {
                // this may be ok - but we should mention it
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("Bad file name in SLDStyleFactory" + fontUrl + "\n" + fne);
                }
            }
        }

        // make sure we have anything to load
        if (is == null) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("null input stream, could not load the font");
            }

            return null;
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("about to load");
        }

        try {
            return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, is);
        } catch (FontFormatException ffe) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("Font format error in SLDStyleFactory " + fontUrl + "\n" + ffe);
            }

            return null;
        } catch (IOException ioe) {
            // we'll ignore this for the moment
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.info("IO error in SLDStyleFactory " + fontUrl + "\n" + ioe);
            }

            return null;
        }
    }

    /**
     * Adds the specified font in the font cache. Useful if you want to load fonts that are not
     * installed in the Operating System and cannot provide a full path to fonts either.
     * 
     * @param f
     */
    public void registerFont(Font f) {
        loadedFonts.put(f.getName(), f);
    }

    /**
     * Resets the font loading cache. If any font was manually registered, it will have to be
     * registered again
     */
    public synchronized void resetCache() {
        if (systemFonts != null) {
            systemFonts.clear();
        }
        if (loadedFonts != null) {
            loadedFonts.clear();
        }
    }

    /**
     * Lazily loads up the system fonts cache
     * 
     * @return
     */
    private Set<String> getSystemFonts() {
        // make sure we load the known font families once.
        if (systemFonts.size() == 0) {
            synchronized (systemFonts) {
                if (systemFonts.size() == 0) {
                    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                    Set<String> fontset = new HashSet<String>();

                    // register both faces and families
                    Font[] fonts = ge.getAllFonts();
                    for (Font font : fonts) {
                        fontset.add(font.getName());
                        fontset.add(font.getFamily());
                    }

                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest("there are " + fontset.size() + " fonts available");
                    }

                    systemFonts.addAll(fontset);
                }
            }
        }

        return systemFonts;
    }

    /**
     * Returns the set of font families and font faces available in the system and those manually
     * loaded into the cache
     * 
     * @return
     */
    public Set<String> getAvailableFonts() {
        Set<String> availableFonts = new HashSet<String>();

        availableFonts.addAll(getSystemFonts());
        availableFonts.addAll(loadedFonts.keySet());

        return availableFonts;
    }

}
