package com.webobjects.foundation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Locale;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.w3c.dom.Document;

public final class _NSStringUtilities {
  public static final String UTF8_ENCODING = "UTF-8";
  
  public static final String UTF16_ENCODING = "UTF-16";
  
  public static final String ASCII_ENCODING = "US-ASCII";
  
  public static final String ISOLATIN1_ENCODING = "ISO-8859-1";
  
  private static final String WO_DEFAULT_ENCODING = "UTF-8";
  
  private static String _encoding = "UTF-8";
  
  public static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
  
  private static final int _MatchState = 0;
  
  private static final int _MatchStarState = 1;
  
  private static final int Finished = 2;
  
  public static void setDefaultEncoding(String encoding) throws UnsupportedEncodingException {
    if (encoding == null) {
      _encoding = "UTF-8";
    } else {
      String testText = "test";
      testText.getBytes(encoding);
      _encoding = encoding;
    } 
  }
  
  public static String defaultEncoding() {
    return _encoding;
  }
  
  public static int integerFromPlist(NSDictionary plist, String key, int defaultValue) {
    int result = defaultValue;
    Object num;
    if ((num = plist.objectForKey(key)) != null)
      if (num instanceof Number) {
        result = ((Number)num).intValue();
      } else {
        result = Integer.parseInt(num.toString());
      }  
    return result;
  }
  
  public static String stringFromBuffer(StringBuffer buffer) {
    int len = buffer.length();
    if (len == 0)
      return ""; 
    return buffer.toString();
  }
  
  public static String stringFromBuffer(StringBuilder buffer) {
    int len = buffer.length();
    if (len == 0)
      return ""; 
    return buffer.toString();
  }
  
  public static final String stringForBytes(byte[] data, int offset, int length, String encoding) {
    String stringEncoding = (encoding != null) ? encoding : defaultEncoding();
    if (stringEncoding == null)
      return new String(data, offset, length); 
    try {
      return (data != null) ? new String(data, offset, length, stringEncoding) : null;
    } catch (UnsupportedEncodingException e) {
      throw NSForwardException._runtimeExceptionForThrowable(e);
    } 
  }
  
  public static final byte[] bytesForString(String text, String encoding) {
    String stringEncoding = (encoding != null) ? encoding : defaultEncoding();
    if (stringEncoding == null)
      return text.getBytes(); 
    try {
      return (text != null) ? text.getBytes(stringEncoding) : null;
    } catch (UnsupportedEncodingException e) {
      throw NSForwardException._runtimeExceptionForThrowable(e);
    } 
  }
  
  public static final String stringForBytes(byte[] data, String encoding) {
    return (data != null) ? stringForBytes(data, 0, data.length, encoding) : null;
  }
  
  public static final String stringForBytes(byte[] data) {
    return stringForBytes(data, defaultEncoding());
  }
  
  public static final byte[] bytesForString(String text) {
    return bytesForString(text, defaultEncoding());
  }
  
  public static final String asciiStringForBytes(byte[] data) {
    return stringForBytes(data, "US-ASCII");
  }
  
  public static final byte[] bytesForAsciiString(String text) {
    return bytesForString(text, "US-ASCII");
  }
  
  public static final String isolatinStringForBytes(byte[] data) {
    return stringForBytes(data, "ISO-8859-1");
  }
  
  public static final byte[] bytesForIsolatinString(String text) {
    return bytesForString(text, "ISO-8859-1");
  }
  
  public static final String dotifyPath(String path1, String path2) {
    return concat(path1, ".", path2);
  }
  
  public static final String concat(String s1, String s2) {
    return s1.concat(s2);
  }
  
  public static final String concat(String s1, String s2, String s3) {
    StringBuffer buffer = new StringBuffer(s1.length() + s2.length() + s3.length());
    return new String(buffer.append(s1).append(s2).append(s3));
  }
  
  public static final String concat(String s1, String s2, String s3, String s4) {
    StringBuffer buffer = new StringBuffer(s1.length() + s2.length() + s3.length() + s4.length());
    return new String(buffer.append(s1).append(s2).append(s3).append(s4));
  }
  
  public static final String concat(String s1, String s2, String s3, String s4, String s5) {
    StringBuffer buffer = new StringBuffer(s1.length() + s2.length() + s3.length() + s4.length() + s5.length());
    return new String(buffer.append(s1).append(s2).append(s3).append(s4).append(s5));
  }
  
  public static final String concat(String s1, String s2, String s3, String s4, String s5, String s6) {
    StringBuffer buffer = new StringBuffer(s1.length() + s2.length() + s3.length() + s4.length() + s5.length() + s6.length());
    return new String(buffer.append(s1).append(s2).append(s3).append(s4).append(s5).append(s6));
  }
  
  public static final String concat(String s1, String s2, String s3, String s4, String s5, String s6, String s7) {
    StringBuffer buffer = new StringBuffer(s1.length() + s2.length() + s3.length() + s4.length() + s5.length() + s6.length() + s7.length());
    return new String(buffer.append(s1).append(s2).append(s3).append(s4).append(s5).append(s6).append(s7));
  }
  
  public static final String concat(String s1, String s2, String s3, String s4, String s5, String s6, String s7, String s8) {
    StringBuffer buffer = new StringBuffer(s1.length() + s2.length() + s3.length() + s4.length() + s5.length() + s6.length() + s7.length() + s8.length());
    return new String(buffer.append(s1).append(s2).append(s3).append(s4).append(s5).append(s6).append(s7).append(s8));
  }
  
  public static final String concat(String s1, String s2, String s3, String s4, String s5, String s6, String s7, String s8, String s9) {
    StringBuffer buffer = new StringBuffer(s1.length() + s2.length() + s3.length() + s4.length() + s5.length() + s6.length() + s7.length() + s8.length() + s9.length());
    return new String(buffer.append(s1).append(s2).append(s3).append(s4).append(s5).append(s6).append(s7).append(s8).append(s9));
  }
  
  public static final String concat(String s1, String s2, String s3, String s4, String s5, String s6, String s7, String s8, String s9, String s10) {
    StringBuffer buffer = new StringBuffer(s1.length() + s2.length() + s3.length() + s4.length() + s5.length() + s6.length() + s7.length() + s8.length() + s9.length() + s10.length());
    return new String(buffer.append(s1).append(s2).append(s3).append(s4).append(s5).append(s6).append(s7).append(s8).append(s9).append(s10));
  }
  
  public static String stringMarkingUpcaseTransitionsWithDelimiter(String self, String delimiter) {
    int len = self.length(), sepLen = delimiter.length();
    int outlen = 0;
    boolean lastWasLower = false;
    char[] inbuf = new char[len + 1];
    char[] outbuf = new char[len * 2 + 1];
    self.getChars(0, len, inbuf, 0);
    for (int i = 0; i < len; i++) {
      char c = inbuf[i];
      if (Character.isUpperCase(c)) {
        if (lastWasLower && i != 0) {
          delimiter.getChars(0, sepLen, outbuf, outlen);
          outlen += sepLen;
        } 
        lastWasLower = false;
      } else {
        lastWasLower = true;
      } 
      outbuf[outlen++] = c;
    } 
    String result = new String(outbuf, 0, outlen);
    return result;
  }
  
  public static String stringRepeatedTimes(String string, int count) {
    StringBuilder buffer = new StringBuilder(string.length() * count);
    for (int i = 0; i < count; i++)
      buffer.append(string); 
    return buffer.toString();
  }
  
  public static String quotedStringWithQuote(String string, char quoteCharacter) {
    char escapeCharacter = '\\';
    int count = (string != null) ? string.length() : 0;
    StringBuilder buffer = new StringBuilder(count + 8);
    buffer.append(quoteCharacter);
    if (string != null)
      for (int i = 0; i < count; i++) {
        char character = string.charAt(i);
        if (character == quoteCharacter || character == escapeCharacter)
          buffer.append(escapeCharacter); 
        buffer.append(character);
      }  
    buffer.append(quoteCharacter);
    return new String(buffer);
  }
  
  public static String capitalizedStringAsWord(String string) {
    int iCount = string.length();
    if (iCount == 0)
      return string; 
    char[] valueAsChar = string.toCharArray();
    valueAsChar[0] = Character.toUpperCase(valueAsChar[0]);
    for (int i = 1; i < iCount; i++)
      valueAsChar[i] = Character.toLowerCase(valueAsChar[i]); 
    return new String(valueAsChar);
  }
  
  public static String capitalizedString(String string) {
    String capitalizedString = string;
    if (capitalizedString != null) {
      int length = capitalizedString.length();
      if (length > 0) {
        char character = capitalizedString.charAt(0);
        if (!Character.isUpperCase(character)) {
          StringBuilder buffer = new StringBuilder(capitalizedString.length());
          buffer.append(Character.toUpperCase(character));
          if (length > 1)
            buffer.append(capitalizedString.substring(1)); 
          capitalizedString = new String(buffer);
        } 
      } 
    } 
    return capitalizedString;
  }
  
  public static String capitalizedStringWithPrefix(String string, String prefix) {
    if (string == null)
      return prefix; 
    if (prefix == null)
      return capitalizedString(string); 
    int length = string.length();
    StringBuffer buffer = new StringBuffer(prefix.length() + length);
    buffer.append(prefix);
    if (length > 0) {
      buffer.append(Character.toUpperCase(string.charAt(0)));
      if (length > 1)
        buffer.append(string.substring(1)); 
    } 
    return new String(buffer);
  }
  
  public static String deleteAllInstancesOfString(String inString, String toBeDeleted) {
    return replaceAllInstancesOfString(inString, toBeDeleted, "");
  }
  
  public static String replaceAllInstancesOfString(String inString, String searchString, String replaceString) {
    char[] temp = (char[])null;
    StringBuffer result = null;
    int index = 0;
    int previous = 0;
    int increment = searchString.length();
    while ((index = inString.indexOf(searchString, index)) != -1) {
      if (result == null) {
        result = new StringBuffer(inString.length());
        temp = inString.toCharArray();
      } 
      if (index > previous)
        result.append(temp, previous, index - previous); 
      if (replaceString.length() > 0)
        result.append(replaceString); 
      index += increment;
      previous = index;
    } 
    if (result == null)
      return inString; 
    if (temp != null && temp.length > previous)
      result.append(temp, previous, temp.length - previous); 
    return new String(result);
  }
  
  public static boolean isNumber(String string) {
    int length = string.length();
    if (length == 0)
      return false; 
    boolean dot = false;
    int i = 0;
    char character = string.charAt(0);
    if (character == '-' || character == '+') {
      i = 1;
    } else if (character == '.') {
      i = 1;
      dot = true;
    } 
    while (i < length) {
      character = string.charAt(i++);
      if (character == '.') {
        if (dot)
          return false; 
        dot = true;
        continue;
      } 
      if (!Character.isDigit(character))
        return false; 
    } 
    return true;
  }
  
  public static String lastComponentInString(String text, char componentSeparator) {
    return lastComponentInString(text, componentSeparator, text);
  }
  
  public static String lastComponentInString(String string, char componentSeparator, String defaultString) {
    if (string != null) {
      int index = string.lastIndexOf(componentSeparator);
      if (index >= 0)
        return (index < string.length() - 1) ? string.substring(index + 1) : ""; 
      return string;
    } 
    return defaultString;
  }
  
  public static String stringByDeletingLastComponent(String string, char componentSeparator) {
    if (string != null) {
      int index = string.lastIndexOf(componentSeparator);
      return (index >= 0) ? string.substring(0, index) : "";
    } 
    return null;
  }
  
  public static boolean containsOnlyWhiteSpace(String string) {
    return (string.trim().length() == 0);
  }
  
  public static void appendToFile(File file, String content) {
    byte[] newData;
    RandomAccessFile target = null;
    try {
      target = new RandomAccessFile(file, "rw");
      long fileLength = target.length();
      target.seek(fileLength);
      byte[] arrayOfByte = bytesForString(content);
      target.write(arrayOfByte, 0, arrayOfByte.length);
      target.close();
      target = null;
    } catch (IOException ex) {
      throw NSForwardException._runtimeExceptionForThrowable(ex);
    } finally {
      try {
        if (target != null)
          target.close(); 
      } catch (Throwable t) {
        if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 8192L)) {
          NSLog.debug.appendln("Exception while closing file output stream: " + t.getMessage());
          NSLog.debug.appendln(t);
        } 
      } 
      target = null;
      byte[] arrayOfByte = (byte[])null;
    } 
  }
  
  public static void writeToFile(File file, String content) {
    FileOutputStream s = null;
    try {
      s = new FileOutputStream(file);
      s.write(bytesForString(content));
      s.flush();
    } catch (IOException e) {
      throw NSForwardException._runtimeExceptionForThrowable(e);
    } finally {
      if (s != null) {
        try {
          s.close();
        } catch (IOException ex) {
          if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 8192L)) {
            NSLog.debug.appendln("Exception while closing file output stream: " + ex.getMessage());
            NSLog.debug.appendln(ex);
          } 
        } 
        s = null;
      } 
    } 
  }
  
  @Deprecated
  public static String stringFromFile(String path) {
    return stringFromFile(path, (String)null);
  }
  
  public static String stringFromFile(String path, String encoding) {
    File file = new File(path);
    return stringFromFile(file, encoding);
  }
  
  @Deprecated
  public static String stringFromFile(File f) {
    return stringFromFile(f, (String)null);
  }
  
  public static String stringFromFile(File f, String encoding) {
    FileInputStream fis = null;
    byte[] data = (byte[])null;
    if (f == null)
      throw new IllegalArgumentException("Cannot open null file object."); 
    if (!f.exists())
      return null; 
    try {
      int size = (int)f.length();
      fis = new FileInputStream(f);
      data = new byte[size];
      int bytesRead = 0;
      while (bytesRead < size)
        bytesRead += fis.read(data, bytesRead, size - bytesRead); 
    } catch (IOException e) {
      throw NSForwardException._runtimeExceptionForThrowable(e);
    } finally {
      if (fis != null) {
        try {
          fis.close();
        } catch (IOException e) {
          if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 8192L)) {
            NSLog.debug.appendln("Exception while closing file input stream: " + e.getMessage());
            NSLog.debug.appendln(e);
          } 
        } 
        fis = null;
      } 
    } 
    return stringForBytes(data, encoding);
  }
  
  public static byte[] bytesFromInputStream(InputStream is) {
    byte[] ret = (byte[])null;
    try {
      int avail = is.available();
      if (avail <= 0)
        avail = 4096; 
      ByteArrayOutputStream baos = new ByteArrayOutputStream(avail);
      byte[] data = new byte[avail];
      while (true) {
        int bytesRead = is.read(data);
        if (bytesRead <= 0)
          break; 
        baos.write(data, 0, bytesRead);
      } 
      ret = baos.toByteArray();
    } catch (IOException e) {
      throw NSForwardException._runtimeExceptionForThrowable(e);
    } finally {
      try {
        is.close();
      } catch (IOException ioe) {
        throw NSForwardException._runtimeExceptionForThrowable(ioe);
      } 
    } 
    return ret;
  }
  
  public static String stringFromInputStream(InputStream is) {
    return stringFromInputStream(is, null);
  }
  
  public static String stringFromInputStream(InputStream is, String encoding) {
    if (is == null)
      return null; 
    try {
      return stringForBytes(bytesFromInputStream(is), encoding);
    } catch (Exception e) {
      throw NSForwardException._runtimeExceptionForThrowable(e);
    } 
  }
  
  public static String stringFromReader(Reader r) {
    if (r == null)
      return null; 
    try {
      StringBuilder builder = new StringBuilder();
      char[] data = new char[2048];
      int count;
      while ((count = r.read(data, 0, data.length)) > 0)
        builder.append(data, 0, count); 
      return new String(builder);
    } catch (Exception e) {
      throw NSForwardException._runtimeExceptionForThrowable(e);
    } 
  }
  
  public static String stringFromPathURL(URL url) {
    return stringFromPathURL(url, null);
  }
  
  public static String stringFromPathURL(URL url, String encoding) {
    if (url == null)
      return null; 
    try {
      return stringFromInputStream(url.openStream(), encoding);
    } catch (Exception e) {
      throw NSForwardException._runtimeExceptionForThrowable(e);
    } 
  }
  
  public static String stringWithReplacements(String self, NSDictionary<?, String> replacements) {
    NSMutableArray<String> result = NSArray._mutableComponentsSeparatedByString(self, " ");
    int c = result.count();
    for (int a = 0; a < c; a++) {
      String atom = (String)result.objectAtIndex(a);
      String replacement;
      if ((replacement = (String)replacements.objectForKey(atom)) != null)
        result.replaceObjectAtIndex(replacement, a); 
    } 
    return result.componentsJoinedByString(" ");
  }
  
  public static String convertDOMToString(Document doc) {
    if (doc == null)
      return null; 
    StringWriter stringOut = new StringWriter();
    try {
      OutputFormat format = new OutputFormat(doc);
      XMLSerializer serial = new XMLSerializer(stringOut, format);
      serial.asDOMSerializer();
      serial.serialize(doc.getDocumentElement());
    } catch (IOException e) {
      throw new NSForwardException(e);
    } 
    return stringOut.toString();
  }
  
  public static String replaceNSStringSlot(String string, NSArray args) {
    if (string == null || args == null || args.count() == 0)
      return string; 
    StringBuffer buffer = new StringBuffer(string.length() + 64);
    int posSlot = 0, prevPos = 0, indexArg = 0;
    while (true) {
      posSlot = string.indexOf("%@", prevPos);
      if (posSlot >= 0) {
        buffer.append(string.substring(prevPos, posSlot));
        buffer.append(args.objectAtIndex(indexArg).toString());
        indexArg++;
      } 
      prevPos = posSlot + 2;
      if (posSlot < 0)
        return new String(buffer); 
    } 
  }
  
  private static boolean _isMagicCharacter(char character) {
    return !(character != '*' && character != '?' && character != '[' && character != ']');
  }
  
  private static boolean _stringMatchesCharacter(String string, char character) {
    _StringAnalyzer analyzer = new _StringAnalyzer(string);
    char stringCharacter = analyzer.nextCharacter();
    while (stringCharacter != '\000') {
      if (stringCharacter == '-') {
        char stringCharacter1 = analyzer.nextCharacter();
        char stringCharacter2 = analyzer.nextCharacter();
        if (stringCharacter1 <= character && character <= stringCharacter2)
          return true; 
      } else if (stringCharacter == character) {
        return true;
      } 
      stringCharacter = analyzer.nextCharacter();
    } 
    return false;
  }
  
  private static String _bracketedStringFromPatternAnalyzer(_PatternAnalyzer patternAnalyzer) {
    char patternCharacter = patternAnalyzer.nextCharacter();
    boolean isMagic = _isMagicCharacter(patternCharacter);
    int i = 0;
    StringBuffer buffer = new StringBuffer(128);
    while (patternCharacter != '\000' && (patternCharacter != ']' || !isMagic)) {
      buffer.append(patternCharacter);
      i++;
      if (patternCharacter == '-') {
        buffer.append(buffer.charAt(i - 2));
        i++;
      } 
      patternCharacter = patternAnalyzer.nextCharacter();
      isMagic = _isMagicCharacter(patternCharacter);
    } 
    if (patternCharacter == '\000' || patternCharacter != ']')
      throw new IllegalArgumentException("Bad pattern " + patternAnalyzer.string() + "for qualifier"); 
    return new String(buffer);
  }
  
  public static boolean stringMatchesPattern(String string, String pattern, boolean caseInsensitive) {
    boolean stringMatchesPattern = false;
    if (string != null) {
      NSMutableArray<Integer> patternStack = new NSMutableArray(16);
      NSMutableArray<Integer> stringStack = new NSMutableArray(16);
      _PatternAnalyzer patternAnalyzer = new _PatternAnalyzer(pattern);
      _StringAnalyzer stringAnalyzer = new _StringAnalyzer(string);
      char patternCharacter = patternAnalyzer.nextCharacter();
      boolean isMagic = _isMagicCharacter(patternCharacter);
      char stringCharacter = stringAnalyzer.nextCharacter();
      int state = 0;
      while (state != 2) {
        switch (state) {
          case 0:
            if (patternCharacter == '\000' && stringCharacter == '\000') {
              state = 2;
              stringMatchesPattern = true;
              continue;
            } 
            if (isMagic && patternCharacter == '*') {
              while (patternCharacter == '*' && isMagic) {
                patternCharacter = patternAnalyzer.nextCharacter();
                isMagic = _isMagicCharacter(patternCharacter);
              } 
              state = 1;
              continue;
            } 
            if (isMagic && patternCharacter == '?' && stringCharacter != '\000') {
              patternCharacter = patternAnalyzer.nextCharacter();
              isMagic = _isMagicCharacter(patternCharacter);
              stringCharacter = stringAnalyzer.nextCharacter();
              state = 0;
              continue;
            } 
            if (isMagic && patternCharacter == '[' && stringCharacter != '\000') {
              String bracketedString = _bracketedStringFromPatternAnalyzer(patternAnalyzer);
              if (_stringMatchesCharacter(bracketedString, stringCharacter)) {
                state = 0;
                patternCharacter = patternAnalyzer.nextCharacter();
                isMagic = _isMagicCharacter(patternCharacter);
                stringCharacter = stringAnalyzer.nextCharacter();
                continue;
              } 
              stringMatchesPattern = false;
              state = 2;
              continue;
            } 
            if (patternCharacter == stringCharacter || (caseInsensitive && Character.toUpperCase(patternCharacter) == stringCharacter)) {
              patternCharacter = patternAnalyzer.nextCharacter();
              isMagic = _isMagicCharacter(patternCharacter);
              stringCharacter = stringAnalyzer.nextCharacter();
              state = 0;
              continue;
            } 
            if (stringStack.count() == 0) {
              state = 2;
              stringMatchesPattern = false;
              continue;
            } 
            stringAnalyzer.setLocation(((Integer)stringStack.lastObject()).intValue());
            stringAnalyzer.refreshCurrentCharacter();
            stringStack.removeLastObject();
            patternAnalyzer.setLocation(((Integer)patternStack.lastObject()).intValue());
            patternAnalyzer.refreshCurrentCharacter();
            patternStack.removeLastObject();
            patternCharacter = patternAnalyzer.nextCharacter();
            isMagic = _isMagicCharacter(patternCharacter);
            stringCharacter = stringAnalyzer.nextCharacter();
            state = 1;
          case 1:
            if (patternCharacter != '?' || !isMagic)
              if (caseInsensitive) {
                char uppercasePatternCharacter = Character.toUpperCase(patternCharacter);
                while (stringCharacter != '\000' && patternCharacter != stringCharacter && uppercasePatternCharacter != stringCharacter)
                  stringCharacter = stringAnalyzer.nextCharacter(); 
              } else {
                while (stringCharacter != '\000' && patternCharacter != stringCharacter)
                  stringCharacter = stringAnalyzer.nextCharacter(); 
              }  
            if (stringCharacter != '\000') {
              stringStack.addObject(_NSUtilities.IntegerForInt(stringAnalyzer.location()));
              patternStack.addObject(_NSUtilities.IntegerForInt(patternAnalyzer.location() - 1));
            } 
            state = 0;
        } 
      } 
    } 
    return stringMatchesPattern;
  }
  
  private static class _StringAnalyzer {
    protected String _string;
    
    protected int _stringLength;
    
    protected int _location;
    
    protected char _currentCharacter;
    
    public _StringAnalyzer(String string) {
      this._string = string;
      this._stringLength = this._string.length();
      this._location = 0;
      this._currentCharacter = (this._stringLength > 0) ? this._string.charAt(this._location) : Character.MIN_VALUE;
    }
    
    public String string() {
      return this._string;
    }
    
    public boolean isAtEnd() {
      return (this._location >= this._stringLength);
    }
    
    public void setLocation(int location) {
      this._location = location;
    }
    
    public int location() {
      return this._location;
    }
    
    public char currentCharacter() {
      return this._currentCharacter;
    }
    
    public void advance() {
      if (!isAtEnd()) {
        this._location++;
        this._currentCharacter = (this._location < this._stringLength) ? this._string.charAt(this._location) : Character.MIN_VALUE;
      } 
    }
    
    public char nextCharacter() {
      if (!isAtEnd()) {
        char character = this._currentCharacter;
        advance();
        return character;
      } 
      return Character.MIN_VALUE;
    }
    
    public void refreshCurrentCharacter() {
      if (!isAtEnd())
        this._currentCharacter = (this._location < this._stringLength) ? this._string.charAt(this._location) : Character.MIN_VALUE; 
    }
  }
  
  private static class _PatternAnalyzer extends _StringAnalyzer {
    public _PatternAnalyzer(String string) {
      super(string);
    }
    
    public char nextCharacter() {
      if (isAtEnd())
        return Character.MIN_VALUE; 
      char character = this._currentCharacter;
      if (character == '\\') {
        advance();
        character = this._currentCharacter;
      } 
      advance();
      return character;
    }
  }
  
  private static MessageDigest shaMessageDigest = null;
  
  private static MessageDigest md5MessageDigest = null;
  
  public static final int HASH_METHOD_MD5 = 0;
  
  public static final int HASH_METHOD_SHA = 1;
  
  public static final char[] HEX_CHARS = new char[] { 
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 
      'a', 'b', 'c', 'd', 'e', 'f' };
  
  static {
    try {
      shaMessageDigest = MessageDigest.getInstance("SHA");
      md5MessageDigest = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException nsae) {
      throw new RuntimeException(nsae);
    } 
  }
  
  public static String md5Hash(String messageString) {
    return getHash(messageString, 0);
  }
  
  public static String shaHash(String messageString) {
    return getHash(messageString, 1);
  }
  
  public static byte[] getHash(byte[] value, int method) {
    if (method != 0 && method != 1)
      throw new IllegalArgumentException("Unknown hash method: " + method); 
    byte[] hash = (method == 1) ? shaMessageDigest.digest(value) : md5MessageDigest.digest(value);
    return hash;
  }
  
  private static String getHash(String messageString, int method) {
    byte[] queryStringBytes = messageString.getBytes();
    byte[] hash = getHash(queryStringBytes, method);
    String result = byteArrayToHexString(hash);
    return result;
  }
  
  public static String toHexString(String str) {
    if (str == null)
      return null; 
    byte[] b = str.getBytes();
    StringBuffer sb = new StringBuffer(b.length * 2);
    for (int i = 0; i < b.length; i++) {
      sb.append(HEX_CHARS[(b[i] & 0xF0) >>> 4]);
      sb.append(HEX_CHARS[b[i] & 0xF]);
    } 
    return sb.toString();
  }
  
  public static String byteArrayToHexString(byte[] block) {
    int len = block.length;
    StringBuffer buf = new StringBuffer(2 * len);
    for (int i = 0; i < len; i++) {
      int high = (block[i] & 0xF0) >> 4;
      int low = block[i] & 0xF;
      buf.append(HEX_CHARS[high]);
      buf.append(HEX_CHARS[low]);
    } 
    return buf.toString();
  }
  
  public static byte[] hexStringToByteArray(String hexString) {
    int length = hexString.length();
    if (length % 2 == 1)
      throw new IllegalArgumentException("String must have even length: " + length); 
    byte[] array = new byte[length / 2];
    for (int i = 0; i < array.length; i++) {
      char c1 = hexString.charAt(i * 2);
      char c2 = hexString.charAt(i * 2 + 1);
      byte b = 0;
      if (c1 >= '0' && c1 <= '9') {
        b = (byte)(b + (c1 - 48) * 16);
      } else if (c1 >= 'a' && c1 <= 'f') {
        b = (byte)(b + (c1 - 97 + 10) * 16);
      } else if (c1 >= 'A' && c1 <= 'F') {
        b = (byte)(b + (c1 - 65 + 10) * 16);
      } else {
        throw new IllegalArgumentException("Illegal Character");
      } 
      if (c2 >= '0' && c2 <= '9') {
        b = (byte)(b + c2 - 48);
      } else if (c2 >= 'a' && c2 <= 'f') {
        b = (byte)(b + c2 - 97 + 10);
      } else if (c2 >= 'A' && c2 <= 'F') {
        b = (byte)(b + c2 - 65 + 10);
      } else {
        throw new IllegalArgumentException("Illegal Character");
      } 
      array[i] = b;
    } 
    return array;
  }
  
  private static final ThreadLocal ArgsArrayThreadLocal = new ThreadLocal();
  
  private static final int MaxArgs = 7;
  
  private static String[] threadLocalArgsArray() {
    String[] argsArray = ArgsArrayThreadLocal.get();
    if (argsArray == null) {
      argsArray = new String[7];
      ArgsArrayThreadLocal.set(argsArray);
    } 
    return argsArray;
  }
  
  public static String format(String formatString, Locale locale, Object... args) {
    Locale _locale = locale;
    if (args == null)
      throw new IllegalArgumentException("Formatter args cannot be null"); 
    if (args.length > 7)
      throw new IllegalArgumentException("Cannot format more than 7 arguments"); 
    if (locale == null)
      _locale = Locale.getDefault(); 
    String[] arrayOfString = threadLocalArgsArray();
    for (int i = 0; i < args.length; i++)
      arrayOfString[i] = nonNullString(args[i]); 
    StringBuilder sb = new StringBuilder();
    Formatter formatter = new Formatter(sb, _locale);
    formatter.format(formatString, (Object[])arrayOfString);
    String formattedString = (sb != null) ? sb.toString() : "";
    return formattedString;
  }
  
  public static String format(String formatString, Object... args) {
    return format(formatString, Locale.US, args);
  }
  
  public static String nonNullString(Object object) {
    String string = (object == null) ? "null" : object.toString();
    return string;
  }
  
  public static boolean encodingsEqual(String encodingName1, String encodingName2) {
    if (encodingName1 == null)
      return (encodingName2 == null); 
    if (encodingName2 == null)
      return false; 
    if (encodingName1.equalsIgnoreCase(encodingName2))
      return true; 
    try {
      Charset charset1 = Charset.forName(encodingName1);
      Charset charset2 = Charset.forName(encodingName2);
      return charset1.equals(charset2);
    } catch (UnsupportedCharsetException e) {
      return false;
    } 
  }
}


/* Location:              /Users/hugi/.m2/repository/wonder/core/ERFoundation/1.0/ERFoundation-1.0.jar!/com/webobjects/foundation/_NSStringUtilities.class
 * Java compiler version: 5 (49.0)
 * JD-Core Version:       1.1.3
 */