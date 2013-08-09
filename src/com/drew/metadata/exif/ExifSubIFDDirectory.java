/*
 * Copyright 2002-2012 Drew Noakes
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 * More information about this project is available at:
 *
 *    http://drewnoakes.com/code/exif/
 *    http://code.google.com/p/metadata-extractor/
 */
package com.drew.metadata.exif;

import com.drew.lang.annotations.NotNull;
import com.drew.metadata.Directory;

import java.util.HashMap;

/**
 * Describes Exif tags from the SubIFD directory.
 *
 * @author Drew Noakes http://drewnoakes.com
 */
public class ExifSubIFDDirectory extends Directory
{
    /**
     * The actual aperture value of lens when the image was taken. Unit is APEX.
     * To convert this value to ordinary F-number (F-stop), calculate this value's
     * power of root 2 (=1.4142). For example, if the ApertureValue is '5',
     * F-number is 1.4142^5 = F5.6.
     */
    public static final int TAG_APERTURE = 0x9202;
    /**
     * When image format is no compression, this value shows the number of bits
     * per component for each pixel. Usually this value is '8,8,8'.
     */
    public static final int TAG_BITS_PER_SAMPLE = 0x0102;

    /**
     * Shows the color space of the image data components.
     * 0 = WhiteIsZero
     * 1 = BlackIsZero
     * 2 = RGB
     * 3 = RGB Palette
     * 4 = Transparency Mask
     * 5 = CMYK
     * 6 = YCbCr
     * 8 = CIELab
     * 9 = ICCLab
     * 10 = ITULab
     * 32803 = Color Filter Array
     * 32844 = Pixar LogL
     * 32845 = Pixar LogLuv
     * 34892 = Linear Raw
     */
    public static final int TAG_PHOTOMETRIC_INTERPRETATION = 0x0106;

    /**
     * 1 = No dithering or halftoning
     * 2 = Ordered dither or halftone
     * 3 = Randomized dither
     */
    public static final int TAG_THRESHOLDING = 0x0107;

    /**
     * 1 = Normal
     * 2 = Reversed
     */
    public static final int TAG_FILL_ORDER = 0x010A;
    public static final int TAG_DOCUMENT_NAME = 0x010D;

    /** The position in the file of raster data. */
    public static final int TAG_STRIP_OFFSETS = 0x0111;
    /** Each pixel is composed of this many samples. */
    public static final int TAG_SAMPLES_PER_PIXEL = 0x0115;
    /** The raster is codified by a single block of data holding this many rows. */
    public static final int TAG_ROWS_PER_STRIP = 0x116;
    /** The size of the raster data in bytes. */
    public static final int TAG_STRIP_BYTE_COUNTS = 0x0117;
    public static final int TAG_MIN_SAMPLE_VALUE = 0x0118;
    public static final int TAG_MAX_SAMPLE_VALUE = 0x0119;
    /**
     * When image format is no compression YCbCr, this value shows byte aligns of
     * YCbCr data. If value is '1', Y/Cb/Cr value is chunky format, contiguous for
     * each subsampling pixel. If value is '2', Y/Cb/Cr value is separated and
     * stored to Y plane/Cb plane/Cr plane format.
     */
    public static final int TAG_PLANAR_CONFIGURATION = 0x011C;
    public static final int TAG_YCBCR_SUBSAMPLING = 0x0212;

    /**
     * The new subfile type tag.
     * 0 = Full-resolution Image
     * 1 = Reduced-resolution image
     * 2 = Single page of multi-page image
     * 3 = Single page of multi-page reduced-resolution image
     * 4 = Transparency mask
     * 5 = Transparency mask of reduced-resolution image
     * 6 = Transparency mask of multi-page image
     * 7 = Transparency mask of reduced-resolution multi-page image
     */
    public static final int TAG_NEW_SUBFILE_TYPE = 0x00FE;
    /**
     * The old subfile type tag.
     * 1 = Full-resolution image (Main image)
     * 2 = Reduced-resolution image (Thumbnail)
     * 3 = Single page of multi-page image
     */
    public static final int TAG_SUBFILE_TYPE = 0x00FF;
    public static final int TAG_TRANSFER_FUNCTION = 0x012D;
    public static final int TAG_PREDICTOR = 0x013D;
    public static final int TAG_TILE_WIDTH = 0x0142;
    public static final int TAG_TILE_LENGTH = 0x0143;
    public static final int TAG_TILE_OFFSETS = 0x0144;
    public static final int TAG_TILE_BYTE_COUNTS = 0x0145;
    public static final int TAG_JPEG_TABLES = 0x015B;
    public static final int TAG_CFA_REPEAT_PATTERN_DIM = 0x828D;
    /** There are two definitions for CFA pattern, I don't know the difference... */
    public static final int TAG_CFA_PATTERN_2 = 0x828E;
    public static final int TAG_BATTERY_LEVEL = 0x828F;
    public static final int TAG_IPTC_NAA = 0x83BB;
    public static final int TAG_INTER_COLOR_PROFILE = 0x8773;
    public static final int TAG_SPECTRAL_SENSITIVITY = 0x8824;
    /**
     * Indicates the Opto-Electric Conversion Function (OECF) specified in ISO 14524.
     * <p/>
     * OECF is the relationship between the camera optical input and the image values.
     * <p/>
     * The values are:
     * <ul>
     *   <li>Two shorts, indicating respectively number of columns, and number of rows.</li>
     *   <li>For each column, the column name in a null-terminated ASCII string.</li>
     *   <li>For each cell, an SRATIONAL value.</li>
     * </ul>
     */
    public static final int TAG_OPTO_ELECTRIC_CONVERSION_FUNCTION = 0x8828;
    public static final int TAG_INTERLACE = 0x8829;
    public static final int TAG_TIME_ZONE_OFFSET = 0x882A;
    public static final int TAG_SELF_TIMER_MODE = 0x882B;
    public static final int TAG_FLASH_ENERGY = 0x920B;
    public static final int TAG_SPATIAL_FREQ_RESPONSE = 0x920C;
    public static final int TAG_NOISE = 0x920D;
    public static final int TAG_IMAGE_NUMBER = 0x9211;
    public static final int TAG_SECURITY_CLASSIFICATION = 0x9212;
    public static final int TAG_IMAGE_HISTORY = 0x9213;
    public static final int TAG_SUBJECT_LOCATION = 0x9214;
    /** There are two definitions for exposure index, I don't know the difference... */
    public static final int TAG_EXPOSURE_INDEX_2 = 0x9215;
    public static final int TAG_TIFF_EP_STANDARD_ID = 0x9216;
    public static final int TAG_FLASH_ENERGY_2 = 0xA20B;
    public static final int TAG_SPATIAL_FREQ_RESPONSE_2 = 0xA20C;
    public static final int TAG_SUBJECT_LOCATION_2 = 0xA214;
    public static final int TAG_PAGE_NAME = 0x011D;
    /**
     * Exposure time (reciprocal of shutter speed). Unit is second.
     */
    public static final int TAG_EXPOSURE_TIME = 0x829A;
    /**
     * The actual F-number(F-stop) of lens when the image was taken.
     */
    public static final int TAG_FNUMBER = 0x829D;
    /**
     * Exposure program that the camera used when image was taken. '1' means
     * manual control, '2' program normal, '3' aperture priority, '4' shutter
     * priority, '5' program creative (slow program), '6' program action
     * (high-speed program), '7' portrait mode, '8' landscape mode.
     */
    public static final int TAG_EXPOSURE_PROGRAM = 0x8822;
    public static final int TAG_ISO_EQUIVALENT = 0x8827;
    public static final int TAG_EXIF_VERSION = 0x9000;
    public static final int TAG_DATETIME_ORIGINAL = 0x9003;
    public static final int TAG_DATETIME_DIGITIZED = 0x9004;
    public static final int TAG_COMPONENTS_CONFIGURATION = 0x9101;
    /**
     * Average (rough estimate) compression level in JPEG bits per pixel.
     * */
    public static final int TAG_COMPRESSED_AVERAGE_BITS_PER_PIXEL = 0x9102;
    /**
     * Shutter speed by APEX value. To convert this value to ordinary 'Shutter Speed';
     * calculate this value's power of 2, then reciprocal. For example, if the
     * ShutterSpeedValue is '4', shutter speed is 1/(24)=1/16 second.
     */
    public static final int TAG_SHUTTER_SPEED = 0x9201;
    public static final int TAG_BRIGHTNESS_VALUE = 0x9203;
    public static final int TAG_EXPOSURE_BIAS = 0x9204;
    /**
     * Maximum aperture value of lens. You can convert to F-number by calculating
     * power of root 2 (same process of ApertureValue:0x9202).
     * The actual aperture value of lens when the image was taken. To convert this
     * value to ordinary f-number(f-stop), calculate the value's power of root 2
     * (=1.4142). For example, if the ApertureValue is '5', f-number is 1.41425^5 = F5.6.
     */
    public static final int TAG_MAX_APERTURE = 0x9205;
    /**
     * Indicates the distance the autofocus camera is focused to.  Tends to be less accurate as distance increases.
     */
    public static final int TAG_SUBJECT_DISTANCE = 0x9206;
    /**
     * Exposure metering method. '0' means unknown, '1' average, '2' center
     * weighted average, '3' spot, '4' multi-spot, '5' multi-segment, '6' partial,
     * '255' other.
     */
    public static final int TAG_METERING_MODE = 0x9207;

    public static final int TAG_LIGHT_SOURCE = 0x9208;
    /**
     * White balance (aka light source). '0' means unknown, '1' daylight,
     * '2' fluorescent, '3' tungsten, '10' flash, '17' standard light A,
     * '18' standard light B, '19' standard light C, '20' D55, '21' D65,
     * '22' D75, '255' other.
     */
    public static final int TAG_WHITE_BALANCE = 0x9208;
    /**
     * 0x0  = 0000000 = No Flash
     * 0x1  = 0000001 = Fired
     * 0x5  = 0000101 = Fired, Return not detected
     * 0x7  = 0000111 = Fired, Return detected
     * 0x9  = 0001001 = On
     * 0xd  = 0001101 = On, Return not detected
     * 0xf  = 0001111 = On, Return detected
     * 0x10 = 0010000 = Off
     * 0x18 = 0011000 = Auto, Did not fire
     * 0x19 = 0011001 = Auto, Fired
     * 0x1d = 0011101 = Auto, Fired, Return not detected
     * 0x1f = 0011111 = Auto, Fired, Return detected
     * 0x20 = 0100000 = No flash function
     * 0x41 = 1000001 = Fired, Red-eye reduction
     * 0x45 = 1000101 = Fired, Red-eye reduction, Return not detected
     * 0x47 = 1000111 = Fired, Red-eye reduction, Return detected
     * 0x49 = 1001001 = On, Red-eye reduction
     * 0x4d = 1001101 = On, Red-eye reduction, Return not detected
     * 0x4f = 1001111 = On, Red-eye reduction, Return detected
     * 0x59 = 1011001 = Auto, Fired, Red-eye reduction
     * 0x5d = 1011101 = Auto, Fired, Red-eye reduction, Return not detected
     * 0x5f = 1011111 = Auto, Fired, Red-eye reduction, Return detected
     *        6543210 (positions)
     *
     * This is a bitmask.
     * 0 = flash fired
     * 1 = return detected
     * 2 = return able to be detected
     * 3 = unknown
     * 4 = auto used
     * 5 = unknown
     * 6 = red eye reduction used
     */
    public static final int TAG_FLASH = 0x9209;
    /**
     * Focal length of lens used to take image.  Unit is millimeter.
     * Nice digital cameras actually save the focal length as a function of how far they are zoomed in.
     */
    public static final int TAG_FOCAL_LENGTH = 0x920A;
    public static final int TAG_USER_COMMENT = 0x9286;
    public static final int TAG_SUBSECOND_TIME = 0x9290;
    public static final int TAG_SUBSECOND_TIME_ORIGINAL = 0x9291;
    public static final int TAG_SUBSECOND_TIME_DIGITIZED = 0x9292;
    public static final int TAG_FLASHPIX_VERSION = 0xA000;
    /**
     * Defines Color Space. DCF image must use sRGB color space so value is
     * always '1'. If the picture uses the other color space, value is
     * '65535':Uncalibrated.
     */
    public static final int TAG_COLOR_SPACE = 0xA001;
    public static final int TAG_EXIF_IMAGE_WIDTH = 0xA002;
    public static final int TAG_EXIF_IMAGE_HEIGHT = 0xA003;
    public static final int TAG_RELATED_SOUND_FILE = 0xA004;
    public static final int TAG_FOCAL_PLANE_X_RES = 0xA20E;
    public static final int TAG_FOCAL_PLANE_Y_RES = 0xA20F;
    /**
     * Unit of FocalPlaneXResolution/FocalPlaneYResolution. '1' means no-unit,
     * '2' inch, '3' centimeter.
     *
     * Note: Some of Fujifilm's digicam(e.g.FX2700,FX2900,Finepix4700Z/40i etc)
     * uses value '3' so it must be 'centimeter', but it seems that they use a
     * '8.3mm?'(1/3in.?) to their ResolutionUnit. Fuji's BUG? Finepix4900Z has
     * been changed to use value '2' but it doesn't match to actual value also.
     */
    public static final int TAG_FOCAL_PLANE_UNIT = 0xA210;
    public static final int TAG_EXPOSURE_INDEX = 0xA215;
    public static final int TAG_SENSING_METHOD = 0xA217;
    public static final int TAG_FILE_SOURCE = 0xA300;
    public static final int TAG_SCENE_TYPE = 0xA301;
    public static final int TAG_CFA_PATTERN = 0xA302;

    // these tags new with Exif 2.2 (?) [A401 - A4
    /**
     * This tag indicates the use of special processing on image data, such as rendering
     * geared to output. When special processing is performed, the reader is expected to
     * disable or minimize any further processing.
     * Tag = 41985 (A401.H)
     * Type = SHORT
     * Count = 1
     * Default = 0
     *   0 = Normal process
     *   1 = Custom process
     *   Other = reserved
     */
    public static final int TAG_CUSTOM_RENDERED = 0xA401;

    /**
     * This tag indicates the exposure mode set when the image was shot. In auto-bracketing
     * mode, the camera shoots a series of frames of the same scene at different exposure settings.
     * Tag = 41986 (A402.H)
     * Type = SHORT
     * Count = 1
     * Default = none
     *   0 = Auto exposure
     *   1 = Manual exposure
     *   2 = Auto bracket
     *   Other = reserved
     */
    public static final int TAG_EXPOSURE_MODE = 0xA402;

    /**
     * This tag indicates the white balance mode set when the image was shot.
     * Tag = 41987 (A403.H)
     * Type = SHORT
     * Count = 1
     * Default = none
     *   0 = Auto white balance
     *   1 = Manual white balance
     *   Other = reserved
     */
    public static final int TAG_WHITE_BALANCE_MODE = 0xA403;

    /**
     * This tag indicates the digital zoom ratio when the image was shot. If the
     * numerator of the recorded value is 0, this indicates that digital zoom was
     * not used.
     * Tag = 41988 (A404.H)
     * Type = RATIONAL
     * Count = 1
     * Default = none
     */
    public static final int TAG_DIGITAL_ZOOM_RATIO = 0xA404;

    /**
     * This tag indicates the equivalent focal length assuming a 35mm film camera,
     * in mm. A value of 0 means the focal length is unknown. Note that this tag
     * differs from the FocalLength tag.
     * Tag = 41989 (A405.H)
     * Type = SHORT
     * Count = 1
     * Default = none
     */
    public static final int TAG_35MM_FILM_EQUIV_FOCAL_LENGTH = 0xA405;

    /**
     * This tag indicates the type of scene that was shot. It can also be used to
     * record the mode in which the image was shot. Note that this differs from
     * the scene type (SceneType) tag.
     * Tag = 41990 (A406.H)
     * Type = SHORT
     * Count = 1
     * Default = 0
     *   0 = Standard
     *   1 = Landscape
     *   2 = Portrait
     *   3 = Night scene
     *   Other = reserved
     */
    public static final int TAG_SCENE_CAPTURE_TYPE = 0xA406;

    /**
     * This tag indicates the degree of overall image gain adjustment.
     * Tag = 41991 (A407.H)
     * Type = SHORT
     * Count = 1
     * Default = none
     *   0 = None
     *   1 = Low gain up
     *   2 = High gain up
     *   3 = Low gain down
     *   4 = High gain down
     *   Other = reserved
     */
    public static final int TAG_GAIN_CONTROL = 0xA407;

    /**
     * This tag indicates the direction of contrast processing applied by the camera
     * when the image was shot.
     * Tag = 41992 (A408.H)
     * Type = SHORT
     * Count = 1
     * Default = 0
     *   0 = Normal
     *   1 = Soft
     *   2 = Hard
     *   Other = reserved
     */
    public static final int TAG_CONTRAST = 0xA408;

    /**
     * This tag indicates the direction of saturation processing applied by the camera
     * when the image was shot.
     * Tag = 41993 (A409.H)
     * Type = SHORT
     * Count = 1
     * Default = 0
     *   0 = Normal
     *   1 = Low saturation
     *   2 = High saturation
     *   Other = reserved
     */
    public static final int TAG_SATURATION = 0xA409;

    /**
     * This tag indicates the direction of sharpness processing applied by the camera
     * when the image was shot.
     * Tag = 41994 (A40A.H)
     * Type = SHORT
     * Count = 1
     * Default = 0
     *   0 = Normal
     *   1 = Soft
     *   2 = Hard
     *   Other = reserved
     */
    public static final int TAG_SHARPNESS = 0xA40A;

    // TODO support this tag (I haven't seen a camera's actual implementation of this yet)

    /**
     * This tag indicates information on the picture-taking conditions of a particular
     * camera model. The tag is used only to indicate the picture-taking conditions in
     * the reader.
     * Tag = 41995 (A40B.H)
     * Type = UNDEFINED
     * Count = Any
     * Default = none
     *
     * The information is recorded in the format shown below. The data is recorded
     * in Unicode using SHORT type for the number of display rows and columns and
     * UNDEFINED type for the camera settings. The Unicode (UCS-2) string including
     * Signature is NULL terminated. The specifics of the Unicode string are as given
     * in ISO/IEC 10464-1.
     *
     *      Length  Type        Meaning
     *      ------+-----------+------------------
     *      2       SHORT       Display columns
     *      2       SHORT       Display rows
     *      Any     UNDEFINED   Camera setting-1
     *      Any     UNDEFINED   Camera setting-2
     *      :       :           :
     *      Any     UNDEFINED   Camera setting-n
     */
    public static final int TAG_DEVICE_SETTING_DESCRIPTION = 0xA40B;

    /**
     * This tag indicates the distance to the subject.
     * Tag = 41996 (A40C.H)
     * Type = SHORT
     * Count = 1
     * Default = none
     *   0 = unknown
     *   1 = Macro
     *   2 = Close view
     *   3 = Distant view
     *   Other = reserved
     */
    public static final int TAG_SUBJECT_DISTANCE_RANGE = 0xA40C;

    /**
     * This tag indicates an identifier assigned uniquely to each image. It is
     * recorded as an ASCII string equivalent to hexadecimal notation and 128-bit
     * fixed length.
     * Tag = 42016 (A420.H)
     * Type = ASCII
     * Count = 33
     * Default = none
     */
    public static final int TAG_IMAGE_UNIQUE_ID = 0xA420;

    /** String. */
    public static final int TAG_CAMERA_OWNER_NAME = 0xA430;
    /** String. */
    public static final int TAG_BODY_SERIAL_NUMBER = 0xA431;
    /** An array of four Rational64u numbers giving focal and aperture ranges. */
    public static final int TAG_LENS_SPECIFICATION = 0xA432;
    /** String. */
    public static final int TAG_LENS_MAKE = 0xA433;
    /** String. */
    public static final int TAG_LENS_MODEL = 0xA434;
    /** String. */
    public static final int TAG_LENS_SERIAL_NUMBER = 0xA435;
    /** Rational64u. */
    public static final int TAG_GAMMA = 0xA500;

    public static final int TAG_LENS = 0xFDEA;

    @NotNull
    protected static final HashMap<Integer, String> _tagNameMap = new HashMap<Integer, String>();

    static
    {
        _tagNameMap.put(TAG_FILL_ORDER, "Fill Order");
        _tagNameMap.put(TAG_DOCUMENT_NAME, "Document Name");
        // TODO why don't these tags have fields associated with them?
        _tagNameMap.put(0x1000, "Related Image File Format");
        _tagNameMap.put(0x1001, "Related Image Width");
        _tagNameMap.put(0x1002, "Related Image Length");
        _tagNameMap.put(0x0156, "Transfer Range");
        _tagNameMap.put(0x0200, "JPEG Proc");
        _tagNameMap.put(TAG_COMPRESSED_AVERAGE_BITS_PER_PIXEL, "Compressed Bits Per Pixel");
        _tagNameMap.put(0x927C, "Maker Note");
        _tagNameMap.put(0xA005, "Interoperability Offset");

        _tagNameMap.put(TAG_NEW_SUBFILE_TYPE, "New Subfile Type");
        _tagNameMap.put(TAG_SUBFILE_TYPE, "Subfile Type");
        _tagNameMap.put(TAG_BITS_PER_SAMPLE, "Bits Per Sample");
        _tagNameMap.put(TAG_PHOTOMETRIC_INTERPRETATION, "Photometric Interpretation");
        _tagNameMap.put(TAG_THRESHOLDING, "Thresholding");
        _tagNameMap.put(TAG_STRIP_OFFSETS, "Strip Offsets");
        _tagNameMap.put(TAG_SAMPLES_PER_PIXEL, "Samples Per Pixel");
        _tagNameMap.put(TAG_ROWS_PER_STRIP, "Rows Per Strip");
        _tagNameMap.put(TAG_STRIP_BYTE_COUNTS, "Strip Byte Counts");
        _tagNameMap.put(TAG_PAGE_NAME, "Page Name");
        _tagNameMap.put(TAG_PLANAR_CONFIGURATION, "Planar Configuration");
        _tagNameMap.put(TAG_TRANSFER_FUNCTION, "Transfer Function");
        _tagNameMap.put(TAG_PREDICTOR, "Predictor");
        _tagNameMap.put(TAG_TILE_WIDTH, "Tile Width");
        _tagNameMap.put(TAG_TILE_LENGTH, "Tile Length");
        _tagNameMap.put(TAG_TILE_OFFSETS, "Tile Offsets");
        _tagNameMap.put(TAG_TILE_BYTE_COUNTS, "Tile Byte Counts");
        _tagNameMap.put(TAG_JPEG_TABLES, "JPEG Tables");
        _tagNameMap.put(TAG_YCBCR_SUBSAMPLING, "YCbCr Sub-Sampling");
        _tagNameMap.put(TAG_CFA_REPEAT_PATTERN_DIM, "CFA Repeat Pattern Dim");
        _tagNameMap.put(TAG_CFA_PATTERN_2, "CFA Pattern");
        _tagNameMap.put(TAG_BATTERY_LEVEL, "Battery Level");
        _tagNameMap.put(TAG_EXPOSURE_TIME, "Exposure Time");
        _tagNameMap.put(TAG_FNUMBER, "F-Number");
        _tagNameMap.put(TAG_IPTC_NAA, "IPTC/NAA");
        _tagNameMap.put(TAG_INTER_COLOR_PROFILE, "Inter Color Profile");
        _tagNameMap.put(TAG_EXPOSURE_PROGRAM, "Exposure Program");
        _tagNameMap.put(TAG_SPECTRAL_SENSITIVITY, "Spectral Sensitivity");
        _tagNameMap.put(TAG_ISO_EQUIVALENT, "ISO Speed Ratings");
        _tagNameMap.put(TAG_OPTO_ELECTRIC_CONVERSION_FUNCTION, "Opto-electric Conversion Function (OECF)");
        _tagNameMap.put(TAG_INTERLACE, "Interlace");
        _tagNameMap.put(TAG_TIME_ZONE_OFFSET, "Time Zone Offset");
        _tagNameMap.put(TAG_SELF_TIMER_MODE, "Self Timer Mode");
        _tagNameMap.put(TAG_EXIF_VERSION, "Exif Version");
        _tagNameMap.put(TAG_DATETIME_ORIGINAL, "Date/Time Original");
        _tagNameMap.put(TAG_DATETIME_DIGITIZED, "Date/Time Digitized");
        _tagNameMap.put(TAG_COMPONENTS_CONFIGURATION, "Components Configuration");
        _tagNameMap.put(TAG_SHUTTER_SPEED, "Shutter Speed Value");
        _tagNameMap.put(TAG_APERTURE, "Aperture Value");
        _tagNameMap.put(TAG_BRIGHTNESS_VALUE, "Brightness Value");
        _tagNameMap.put(TAG_EXPOSURE_BIAS, "Exposure Bias Value");
        _tagNameMap.put(TAG_MAX_APERTURE, "Max Aperture Value");
        _tagNameMap.put(TAG_SUBJECT_DISTANCE, "Subject Distance");
        _tagNameMap.put(TAG_METERING_MODE, "Metering Mode");
        _tagNameMap.put(TAG_LIGHT_SOURCE, "Light Source");
        _tagNameMap.put(TAG_WHITE_BALANCE, "White Balance");
        _tagNameMap.put(TAG_FLASH, "Flash");
        _tagNameMap.put(TAG_FOCAL_LENGTH, "Focal Length");
        _tagNameMap.put(TAG_FLASH_ENERGY, "Flash Energy");
        _tagNameMap.put(TAG_SPATIAL_FREQ_RESPONSE, "Spatial Frequency Response");
        _tagNameMap.put(TAG_NOISE, "Noise");
        _tagNameMap.put(TAG_IMAGE_NUMBER, "Image Number");
        _tagNameMap.put(TAG_SECURITY_CLASSIFICATION, "Security Classification");
        _tagNameMap.put(TAG_IMAGE_HISTORY, "Image History");
        _tagNameMap.put(TAG_SUBJECT_LOCATION, "Subject Location");
        _tagNameMap.put(TAG_EXPOSURE_INDEX, "Exposure Index");
        _tagNameMap.put(TAG_TIFF_EP_STANDARD_ID, "TIFF/EP Standard ID");
        _tagNameMap.put(TAG_USER_COMMENT, "User Comment");
        _tagNameMap.put(TAG_SUBSECOND_TIME, "Sub-Sec Time");
        _tagNameMap.put(TAG_SUBSECOND_TIME_ORIGINAL, "Sub-Sec Time Original");
        _tagNameMap.put(TAG_SUBSECOND_TIME_DIGITIZED, "Sub-Sec Time Digitized");
        _tagNameMap.put(TAG_FLASHPIX_VERSION, "FlashPix Version");
        _tagNameMap.put(TAG_COLOR_SPACE, "Color Space");
        _tagNameMap.put(TAG_EXIF_IMAGE_WIDTH, "Exif Image Width");
        _tagNameMap.put(TAG_EXIF_IMAGE_HEIGHT, "Exif Image Height");
        _tagNameMap.put(TAG_RELATED_SOUND_FILE, "Related Sound File");
        // 0x920B in TIFF/EP
        _tagNameMap.put(TAG_FLASH_ENERGY_2, "Flash Energy");
        // 0x920C in TIFF/EP
        _tagNameMap.put(TAG_SPATIAL_FREQ_RESPONSE_2, "Spatial Frequency Response");
        // 0x920E in TIFF/EP
        _tagNameMap.put(TAG_FOCAL_PLANE_X_RES, "Focal Plane X Resolution");
        // 0x920F in TIFF/EP
        _tagNameMap.put(TAG_FOCAL_PLANE_Y_RES, "Focal Plane Y Resolution");
        // 0x9210 in TIFF/EP
        _tagNameMap.put(TAG_FOCAL_PLANE_UNIT, "Focal Plane Resolution Unit");
        // 0x9214 in TIFF/EP
        _tagNameMap.put(TAG_SUBJECT_LOCATION_2, "Subject Location");
        // 0x9215 in TIFF/EP
        _tagNameMap.put(TAG_EXPOSURE_INDEX_2, "Exposure Index");
        // 0x9217 in TIFF/EP
        _tagNameMap.put(TAG_SENSING_METHOD, "Sensing Method");
        _tagNameMap.put(TAG_FILE_SOURCE, "File Source");
        _tagNameMap.put(TAG_SCENE_TYPE, "Scene Type");
        _tagNameMap.put(TAG_CFA_PATTERN, "CFA Pattern");

        _tagNameMap.put(TAG_CUSTOM_RENDERED, "Custom Rendered");
        _tagNameMap.put(TAG_EXPOSURE_MODE, "Exposure Mode");
        _tagNameMap.put(TAG_WHITE_BALANCE_MODE, "White Balance Mode");
        _tagNameMap.put(TAG_DIGITAL_ZOOM_RATIO, "Digital Zoom Ratio");
        _tagNameMap.put(TAG_35MM_FILM_EQUIV_FOCAL_LENGTH, "Focal Length 35");
        _tagNameMap.put(TAG_SCENE_CAPTURE_TYPE, "Scene Capture Type");
        _tagNameMap.put(TAG_GAIN_CONTROL, "Gain Control");
        _tagNameMap.put(TAG_CONTRAST, "Contrast");
        _tagNameMap.put(TAG_SATURATION, "Saturation");
        _tagNameMap.put(TAG_SHARPNESS, "Sharpness");
        _tagNameMap.put(TAG_DEVICE_SETTING_DESCRIPTION, "Device Setting Description");
        _tagNameMap.put(TAG_SUBJECT_DISTANCE_RANGE, "Subject Distance Range");
        _tagNameMap.put(TAG_IMAGE_UNIQUE_ID, "Unique Image ID");
        
        _tagNameMap.put(TAG_CAMERA_OWNER_NAME, "Camera Owner Name");
        _tagNameMap.put(TAG_BODY_SERIAL_NUMBER, "Body Serial Number");
        _tagNameMap.put(TAG_LENS_SPECIFICATION, "Lens Specification");
        _tagNameMap.put(TAG_LENS_MAKE, "Lens Make");
        _tagNameMap.put(TAG_LENS_MODEL, "Lens Model");
        _tagNameMap.put(TAG_LENS_SERIAL_NUMBER, "Lens Serial Number");
        _tagNameMap.put(TAG_GAMMA, "Gamma");

        _tagNameMap.put(TAG_MIN_SAMPLE_VALUE, "Minimum sample value");
        _tagNameMap.put(TAG_MAX_SAMPLE_VALUE, "Maximum sample value");

        _tagNameMap.put(TAG_LENS, "Lens");
    }

    public ExifSubIFDDirectory()
    {
        this.setDescriptor(new ExifSubIFDDescriptor(this));
    }

    @NotNull
    public String getName()
    {
        return "Exif SubIFD";
    }

    @NotNull
    protected HashMap<Integer, String> getTagNameMap()
    {
        return _tagNameMap;
    }
}
