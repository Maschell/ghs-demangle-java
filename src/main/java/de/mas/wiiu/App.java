package de.mas.wiiu;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hello world!
 *
 */
public class App {

    static String[] templatePrefixes = new String[] { "tm", "ps", "pt" /* XXX from libiberty cplus-dem.c */ };
    static Map<String, String> baseNames;

    static Map<Character, String> baseTypes;
    static Map<Character, String> typePrefixes;
    static Map<Character, String> typeSuffixes;

    public static void main(String[] args) {
        init();
        
        
        demangle("__sti___17_RZApplication_cpp_8fdb2739");
     
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader("functions.txt"));
            String line = reader.readLine();
            while (line != null) {
                String newStr = line;
                try {
                    newStr = demangle(line);
                    
                       // System.out.println(newStr);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    
                    System.out.println(newStr);
                }               
                // read next line
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void init() {
        baseNames = new HashMap<>();
        baseNames.put("__vtbl", " virtual table");
        baseNames.put("__ct", "#");
        baseNames.put("__dt", "~#");
        baseNames.put("__as", "operator=");
        baseNames.put("__eq", "operator==");
        baseNames.put("__ne", "operator!=");
        baseNames.put("__gt", "operator>");
        baseNames.put("__lt", "operator<");
        baseNames.put("__ge", "operator>=");
        baseNames.put("__le", "operator<=");
        baseNames.put("__pp", "operator++");
        baseNames.put("__pl", "operator+");
        baseNames.put("__apl", "operator+=");
        baseNames.put("__mi", "operator-");
        baseNames.put("__ami", "operator-=");
        baseNames.put("__ml", "operator*");
        baseNames.put("__amu", "operator*=");
        baseNames.put("__dv", "operator/");
        /* XXX below baseNames have not been seen - guess from libiberty cplus-dem.c */
        baseNames.put("__adv", "operator/=");
        baseNames.put("__nw", "operator new");
        baseNames.put("__dl", "operator delete");
        baseNames.put("__vn", "operator new[]");
        baseNames.put("__vd", "operator delete[]");
        baseNames.put("__md", "operator%");
        baseNames.put("__amd", "operator%=");
        baseNames.put("__mm", "operator--");
        baseNames.put("__aa", "operator&&");
        baseNames.put("__oo", "operator||");
        baseNames.put("__or", "operator|");
        baseNames.put("__aor", "operator|=");
        baseNames.put("__er", "operator^");
        baseNames.put("__aer", "operator^=");
        baseNames.put("__ad", "operator&");
        baseNames.put("__aad", "operator&=");
        baseNames.put("__co", "operator~");
        baseNames.put("__cl", "operator()");
        baseNames.put("__ls", "operator<<");
        baseNames.put("__als", "operator<<=");
        baseNames.put("__rs", "operator>>");
        baseNames.put("__ars", "operator>>=");
        baseNames.put("__rf", "operator->");
        baseNames.put("__vc", "operator[]");
        baseTypes = new HashMap<>();
        baseTypes.put('v', "void");
        baseTypes.put('i', "int");
        baseTypes.put('s', "short");
        baseTypes.put('c', "char");
        baseTypes.put('w', "wchar_t");
        baseTypes.put('b', "bool");
        baseTypes.put('f', "float");
        baseTypes.put('d', "double");
        baseTypes.put('l', "long");
        baseTypes.put('L', "long long");
        baseTypes.put('e', "...");
        /* XXX below baseTypes have not been seen - guess from libiberty cplus-dem.c */
        baseTypes.put('r', "long double");
        typePrefixes = new HashMap<>();
        typePrefixes.put('U', "unsigned");
        typePrefixes.put('S', "signed");
        /* XXX below typePrefixes have not been seen - guess from libiberty cplus-dem.c */
        typePrefixes.put('J', "__complex");
        typeSuffixes = new HashMap<>();
        typeSuffixes.put('P', "*");
        typeSuffixes.put('R', "&");
        typeSuffixes.put('C', "const");
        typeSuffixes.put('V', "volatile"); /* XXX this is a guess! */
        /* XXX below typeSuffixes have not been seen - guess from libiberty cplus-dem.c */
        typeSuffixes.put('u', "restrict");

    }

    private static String demangle(String name) {
        if(name.startsWith("__sti__")) {
            return name;
        }
        name = Decompress(name);

        /*
         * This demangle method has basically turned into a hand-written LL(1) recursive descent parser.
         */

        StringWrapper mangle = new StringWrapper();
        String baseName = ReadBaseName(name, mangle);

        /* TODO this may not be right - see S below Q */
        /* h__S__Q1_3clsFi => static cls::h(int) */
        String declStatic;
        if (mangle.getValue().startsWith("S__")) {
            declStatic = "static ";
            mangle.setValue(mangle.getValue().substring(3));
        } else {
            declStatic = "";
        }
        String declNameSpace, declClass;
        if (mangle.getValue().startsWith("Q")) {
            declNameSpace = ReadNameSpace(mangle.getValue(), mangle);

            int last = declNameSpace.lastIndexOf("::");
            if (last != -1)
                declClass = declNameSpace.substring(last + 2);
            else
                declClass = declNameSpace;

            declNameSpace += "::";
        } else if (mangle.getValue().length() > 0 && Character.isDigit(mangle.getValue().charAt(0))) {
            declClass = ReadString(mangle.getValue(), mangle);
            declNameSpace = declClass + "::";
        } else {
            declNameSpace = "";
            declClass = "";
        }

        baseName = baseName.replace("#", declClass);

        /* static */
        if (mangle.getValue().startsWith("S")) {
            declStatic = "static ";
            mangle.setValue(mangle.getValue().substring(1));
        }

        String declConst;
        if (mangle.getValue().startsWith("C")) {
            declConst = " const";
            mangle.setValue(mangle.getValue().substring(1));
        } else
            declConst = "";

        String declType;
        if (mangle.getValue().startsWith("F"))
            declType = ReadType(null, mangle.getValue(), mangle);
        else
            declType = "#";

        /* XXX bit of a hack - some names I see seem to end with _<number> */
        int end;
        if (mangle.getValue().startsWith("_")) {
            try {
                end = Integer.parseInt(mangle.getValue().substring(1));

                baseName += "_" + end;
                mangle.setValue("");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (mangle.getValue().length() > 0)
            throw new IllegalArgumentException("Unknown modifier: \"" + mangle.getValue().charAt(0) + "\".");

        return (declStatic + declType.replace("(#)", " " + declNameSpace + baseName).replace("#", declNameSpace + baseName) + declConst)
                .replace("::" + baseNames.get("__vtbl"), baseNames.get("__vtbl"));
    }

    private static String ReadNameSpace(String name, StringWrapper remainder) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Unexpected end of string. Expected \"Q\".");
        if (!name.startsWith("Q")) throw new IllegalArgumentException("Unexpected character \"" + name.charAt(0) + "\". Expected \"Q\".");

        StringWrapper outWrap = new StringWrapper();
        int count = ReadInt(name.substring(1), outWrap);
        name = outWrap.getValue();

        if (count == 0) throw new IllegalArgumentException("Bad namespace count \"" + count + "\".");
        if (name.length() == 0) throw new IllegalArgumentException("Unexpected end of string. Expected \"_\".");
        if (!name.startsWith("_")) throw new IllegalArgumentException("Unexpected character after namespace count \"" + name.charAt(0) + "\". Expected \"_\".");

        remainder.setValue(name.substring(1));

        String result = "";
        for (int j = 0; j < count; j++) {
            String current;
            if (remainder.getValue().startsWith("Z")) {
                int end = remainder.getValue().indexOf("Z", 1);

                if (end == -1) throw new IllegalArgumentException("Unexpected end of string. Expected \"Z\".");

                current = remainder.getValue().substring(0, end);
                remainder.setValue(name.substring(end + 1));
            } else {

                current = ReadString(remainder.getValue(), remainder);
            }

            result += (result.length() > 0 ? "::" : "") + current;
        }

        return result;
    }

    private static String ReadType(List<String> args, String name, StringWrapper remainder) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("Unexpected end of string. Expected a type.");

        /* e.g. "i" => "int#" */
        if (baseTypes.containsKey(name.charAt(0))) {
            remainder.setValue(name.substring(1));
            return baseTypes.get(name.charAt(0)) + "#";
        }
        /* e.g. "Q2_3std4move__tm__2_w" => "std::move<wchar_t>#" */
        else if (name.startsWith("Q"))
            return ReadNameSpace(name, remainder) + "#";
        /* e.g. "8MyStruct" => "MyStruct#" */
        else if (Character.isDigit(name.charAt(0)))
            return ReadString(name, remainder) + "#";
        /* e.g. "ui" => "unsigned int#" */
        else if (typePrefixes.containsKey(name.charAt(0)))
            return typePrefixes.get(name.charAt(0)) + " " + ReadType(args, name.substring(1), remainder);
        /* e.g. "Pv" => "void *#" */
        else if (typeSuffixes.containsKey(name.charAt(0)))
            return ReadType(args, name.substring(1), remainder).replace("#", " " + typeSuffixes.get(name.charAt(0)) + "#");
        /* e.g. "Z1Z" => "Z1#" */
        else if (name.startsWith("Z")) {
            int end = name.indexOf("Z", 1);
            if (end == -1) throw new IllegalArgumentException("Unexpected end of string. Expected \"Z\".");

            remainder.setValue(name.substring(end + 1));
            return name.substring(0, end) + "#";
        }
        /* e.g. "A2_i" => "int#[2]" */
        else if (name.startsWith("A")) {
            String len;

            name = name.substring(1);

            if (name.startsWith("_Z")) {
                int end = name.indexOf("Z", 2);

                if (end == -1) throw new IllegalArgumentException("Unexpected end of string. Expected \"Z\".");

                len = name.substring(1, 1 + end - 1);
                name = name.substring(end + 1);
            } else {
                StringWrapper nameWrapper = new StringWrapper();
                len = Integer.toString(ReadInt(name, nameWrapper));
                name = nameWrapper.getValue();
            }

            if (name.isEmpty()) throw new IllegalArgumentException("Unexpected end of string. Expected \"_\".");
            if (!name.startsWith("_"))
                throw new IllegalArgumentException("Unexpected character after array length \"" + name.charAt(0) + "\". Expected \"_\".");

            return ReadType(args, name.substring(1), remainder).replace("#", "#[" + len + "]");
        }
        /* e.g. "FPv_v" => "void (#)(void *)" */
        else if (name.startsWith("F")) {
            StringWrapper nameWrapper = new StringWrapper();
            String declArgs = ReadArguments(name.substring(1), nameWrapper);
            name = nameWrapper.getValue();

            /* XXX bit of a hack - we're allowed not to have a return type on top level methods, which we detected by the args argument being null. */

            boolean parseable = false;
            try {
                if (name.length() > 0) {
                    Integer.parseInt(name.substring(1));
                    parseable = true;
                }
            } catch (NumberFormatException e) {
                parseable = false;
            }
            if (args == null) {
                if (name.length() == 0 || (name.startsWith("_") && parseable)) {
                    remainder.setValue(name);
                    return "#(" + declArgs + ")";
                }

            }

            if (name.length() == 0) throw new IllegalArgumentException("Unexpected end of string. Expected \"_\".");
            if (!name.startsWith("_"))
                throw new IllegalArgumentException("Unexpected character after argument declaration \"" + name.charAt(0) + "\". Expected \"_\".");

            return ReadType(args, name.substring(1), remainder).replace("#", "(#)(" + declArgs + ")");
        }
        /* T<a> expands to argument <a> */
        else if (name.startsWith("T")) {
            if (name.length() < 2) throw new IllegalArgumentException("Unexpected end of string. Expected \"_\".");
            if (!Character.isDigit(name.charAt(1))) throw new IllegalArgumentException("Unexpected character \"" + name.charAt(1) + "\". Expected a digit.");

            int arg = Integer.parseInt(name.substring(1, 2));            

            remainder.setValue(name.substring(2));

            if (args.size() < arg) throw new IllegalArgumentException("Bad argument number \"" + arg + "\".");

            return args.get(arg - 1);
        }
        /* N<c><a> expands to <c> repetitions of argument <a> */
        else if (name.startsWith("N")) {
            if (name.length() < 3) throw new IllegalArgumentException("Unexpected end of string. Expected \"_\".");
            if (!Character.isDigit(name.charAt(1)) || !Character.isDigit(name.charAt(2)))
                throw new IllegalArgumentException("Unexpected character(s) \"" + name.charAt(1) + name.charAt(2) + "\". Expected two digits.");

            int count = Integer.parseInt(name.substring(1, 2));
            int arg = Integer.parseInt(name.substring(2, 3));

            if (count > 1)
                remainder.setValue("N" + (count - 1) + arg + name.substring(3));
            else
                remainder.setValue(name.substring(3));

            if (args.size() < arg) throw new IllegalArgumentException("Bad argument number \"" + arg + "\".");

            return args.get(arg - 1);
        } else
            throw new IllegalArgumentException("Unknown type: \"" + name.charAt(0) + "\".");
    }

    private static String ReadArguments(String name, StringWrapper remainder) {
        String result = "";
        List<String> args = new ArrayList<>();

        remainder.setValue(name);

        while (remainder.getValue().length() > 0 && !remainder.getValue().startsWith("_")) {
            if (args.size() > 0) result += ", ";

            String t = ReadType(args, remainder.getValue(), remainder);
            result += t.replace("#", "");
            args.add(t);
        }

        return result;
    }

    private static String ReadString(String name, StringWrapper remainder) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Unexpected end of string. Expected a digit.");
        }

        StringWrapper nameWrapper = new StringWrapper();
        int len = ReadInt(name, nameWrapper);
        name = nameWrapper.getValue();
        if (len == 0 || name.length() < len) throw new IllegalArgumentException("Bad string length \"" + len + "\".");

        remainder.setValue(name.substring(len));
        return DemangleTemplate(name.substring(0, len));
    }

    static boolean StartsWithAny(String str, String[] names) {
        for (String s : names)
            if (str.startsWith(s)) return true;
        return false;
    }

    private static String DemangleTemplate(String name) {
        int mstart;

        mstart = name.indexOf("__", 1);

        /* check for something like "h___tm_2_i" => "h_<int>" */
        if (mstart != -1 && name.substring(mstart).startsWith("___")) mstart++;

        /* not a special symbol name! */
        if (mstart == -1) return name;

        /* something more interesting! */
        String remainder = name.substring(mstart + 2);
        name = name.substring(0, mstart);

        while (true) {
            if (!StartsWithAny(remainder, templatePrefixes)) throw new IllegalArgumentException("Unexpected template argument prefix.");

            /* format of remainder should be <type>__<len>_<arg> */
            int lstart = remainder.indexOf("__");

            if (lstart == -1) throw new IllegalArgumentException("Bad template argument: \"" + remainder + "\".");

            remainder = remainder.substring(lstart + 2);

            StringWrapper wrapout = new StringWrapper();

            int len = ReadInt(remainder, wrapout);
            remainder = wrapout.getValue();

            if (len == 0 || len > remainder.length()) throw new IllegalArgumentException("Bad template argument length: \"" + len + "\".");
            if (!remainder.startsWith("_"))
                throw new IllegalArgumentException("Unexpected character after template argument length \"" + remainder.charAt(0) + "\". Expected \"_\".");

            String tmp;
            StringWrapper tmpWrap = new StringWrapper();
            String declArgs = ReadTemplateArguments(remainder.substring(1), tmpWrap);
            tmp = tmpWrap.getValue();

            /* avoid emitting the ">>" token */
            if (declArgs.endsWith(">")) declArgs += " ";

            name += "<" + declArgs + ">";
            remainder = remainder.substring(len);

            if (!tmp.contentEquals(remainder)) throw new IllegalArgumentException("Bad template argument length: \"" + len + "\".");

            /* check if we've hit the end */
            if (remainder.length() == 0) return name;

            /* should be immediately followed with __ */
            if (!remainder.startsWith("__"))
                throw new IllegalArgumentException("Unexpected character(s) after template: \"" + remainder.charAt(0) + "\". Expected \"__\".");
            remainder = remainder.substring(2);
        }
    }

    private static String ReadTemplateArguments(String name, StringWrapper remainder) {
        String result = "";
        List<String> args = new ArrayList<>();

        remainder.setValue(name);

        while (remainder.getValue().length() > 0 && !remainder.getValue().startsWith("_")) {
            if (args.size() > 0) result += ", ";

            String type, val;

            if (remainder.getValue().startsWith("X")) {
                /* X arguments represent named values */

                remainder.setValue(remainder.getValue().substring(1));
                if (remainder.getValue().length() == 0) throw new IllegalArgumentException("Unexpected end of string. Expected a type.");

                if (Character.isDigit(remainder.getValue().charAt(0))) {
                    /* arbitrary string */
                    type = "#";

                    val = ReadString(remainder.getValue(), remainder);
                } else {
                    /* <type><encoding> */
                    type = ReadType(args, remainder.getValue(), remainder).replace("#", " #");

                    if (remainder.getValue().startsWith("L")) {
                        /* _<len>_<val> */
                        remainder.setValue(remainder.getValue().substring(1));
                        if (remainder.getValue().length() == 0) throw new IllegalArgumentException("Unexpected end of string. Expected \"_\".");
                        if (!remainder.getValue().startsWith("_")) throw new IllegalArgumentException(
                                "Unexpected character after template parameter encoding \"" + remainder.getValue().charAt(0) + "\". Expected \"_\".");

                        int len = ReadInt(remainder.getValue().substring(1), remainder);

                        if (len == 0 || len > remainder.getValue().length() + 1)
                            throw new IllegalArgumentException("Bad template parameter length: \"" + len + "\".");
                        if (!remainder.getValue().startsWith("_")) throw new IllegalArgumentException(
                                "Unexpected character after template parameter length \"" + remainder.getValue().charAt(0) + "\". Expected \"_\".");

                        remainder.setValue(remainder.getValue().substring(1));
                        val = remainder.getValue().substring(0, len);
                        remainder.setValue(remainder.getValue().substring(len));
                    } else
                        throw new IllegalArgumentException("Unknown template parameter encoding: \"" + remainder.getValue().charAt(0) + "\".");
                }
            } else {
                val = ReadType(args, remainder.getValue(), remainder).replace("#", "");
                type = "class #";
            }

            /* TODO - the Z notation is ugly - we should resolve args? */
            result += type.replace("#", "Z" + (args.size() + 1) + " = " + val);
            args.add(val);
        }

        return result;
    }

    private static int ReadInt(String name, StringWrapper nameWrapper) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Unexpected end of string. Expected a digit.");
        }
        if (!Character.isDigit(name.charAt(0))) {
            throw new IllegalArgumentException("Unexpected character \"" + name.charAt(0) + "\". Expected a digit.");
        }

        int i = 1;
        while (i < name.length() && Character.isDigit(name.charAt(i))) {
            i++;
        }

        nameWrapper.setValue(name.substring(i));

        return Integer.parseInt(name.substring(0, i));
    }

    private static String Decompress(String name) {
        if (!name.startsWith("__CPR")) return name;

        name = name.substring(5);

        StringWrapper outWrap = new StringWrapper();
        int decompressedLen = ReadInt(name, outWrap);
        name = outWrap.getValue();

        if (name.length() == 0) throw new IllegalArgumentException("Unexpected end of string. Expected compressed symbol name.");
        if (!name.startsWith("__"))
            throw new IllegalArgumentException("Unexpected character(s) after compression len: \"" + name.charAt(0) + "\". Expected \"__\".");
        name = name.substring(2);

        String result = "";
        int index = 0;

        /* find all instances of J<num>J */
        while (true) {
            int start = name.indexOf('J', index);

            if (start != -1) {
                result += name.substring(index, index + start - index);

                int end = name.indexOf('J', start + 1);

                if (end != -1) {
                    boolean valid = true;

                    /* check all characters between Js are digits */
                    for (int i = start + 1; i < end; i++)
                        if (!Character.isDigit(name.charAt(i))) {
                            valid = false;
                            break;
                        }

                    if (end > start) valid = false;

                    if (valid) {

                        int loc = Integer.parseInt(name.substring(start + 1, start + 1 + end - start - 1));

                        String tmp;
                        StringWrapper tmpWrap = new StringWrapper();
                        int len = ReadInt(result.substring(loc), tmpWrap);
                        tmp = tmpWrap.getValue();

                        if (len == 0 || len > tmp.length()) throw new IllegalArgumentException("Bad string length \"" + len + "\".");

                        result += len + tmp.substring(0, len);
                        index = end + 1;
                    } else {
                        result += name.substring(start, start + 1);
                        index = start + 1;
                    }
                } else {
                    result += name.substring(start, start + 1);
                    index = start + 1;
                }
            } else {
                result += name.substring(index);
                break;
            }
        }

        if (result.length() != decompressedLen) {
            throw new IllegalArgumentException("Bad decompression length length \"" + decompressedLen + "\". Expected \"" + result.length() + "\".");
        }

        return result;
    }

    private static String ReadBaseName(String name, StringWrapper remainder) {
        String opName;
        int mstart;

        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Unexpected end of string. Expected a name.");
        }

        if (name.startsWith("__op")) {
            StringWrapper stringOut = new StringWrapper();
            /* a cast operator */
            String type = ReadType(null, name.substring(4), stringOut).replace("#", "");
            name = stringOut.getValue();
            opName = "operator " + type;
            name = "#" + name;
        } else {
            opName = "";
        }

        mstart = name.indexOf("__", 1);

        /* check for something like "h___Fi" => "h_" */
        if (mstart != -1 && name.substring(mstart).startsWith("___")) mstart++;

        /* not a special symbol name! */
        if (mstart == -1) {
            remainder.setValue("");
            return name;
        }

        /* something more interesting! */
        remainder.setValue(name.substring(mstart + 2));
        name = name.substring(0, mstart);

        /* check for "__ct__7MyClass" */
        if (baseNames.containsKey(name))
            name = baseNames.get(name);
        else if (name.equals("#")) name = opName;

        while (StartsWithAny(remainder.getValue(), templatePrefixes)) {
            /* format of remainder should be <type>__<len>_<arg> */
            int lstart = remainder.getValue().indexOf("__");

            if (lstart == -1) throw new IllegalArgumentException("Bad template argument: \"" + remainder + "\".");

            /* shift across the template type */
            name += "__" + remainder.getValue().substring(0, lstart);
            remainder.setValue(remainder.getValue().substring(lstart + 2));

            int len = ReadInt(remainder.getValue(), remainder);

            if (len == 0 || len > remainder.getValue().length()) throw new IllegalArgumentException("Bad template argument length: \"" + len + "\".");

            /* shift across the len and arg */
            name += "__" + len + remainder.getValue().substring(0, len);
            remainder.setValue(remainder.getValue().substring(len));

            /* check if we've hit the end */
            if (remainder.getValue().length() == 0) return name;

            /* should be immediately followed with __ */
            if (!remainder.getValue().startsWith("__"))
                throw new IllegalArgumentException("Unexpected character(s) after template: \"" + remainder.getValue().charAt(0) + "\". Expected \"__\".");
            remainder.setValue(remainder.getValue().substring(2));
        }

        return DemangleTemplate(name);
    }
}
