/*
 *    This file is part of HMMoC 1.3, a hidden Markov model compiler.
 *    Copyright (C) 2007 by Gerton Lunter, Oxford University.
 *
 *    HMMoC is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 2 of the License, or
 *    (at your option) any later version.
 *
 *    HMMOC is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with HMMoC; if not, write to the Free Software
 *    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
\*/
package hmmoc.code;


import java.util.*;

import hmmoc.xml.Code;


public class Book {

    Text root;            // points to the final line
    Text cursor;          // points to line before which code is currently written
    ArrayList stack;
    TreeMap scopeMap;
    int stackPointer;
    TreeMap objects;

    public class Scope {

        Text[] pointers;  // points to various accessible blocks, to write code in, or to hold 'return address'
        String label;

        public Scope(int num, String lbl) {

            pointers = new Text[num];
            label = lbl;
        }
    }

    public Book(TreeMap snippetObjects) {

        objects = snippetObjects;
        stack = new ArrayList(0);
        scopeMap = new TreeMap();
        stackPointer = 0;

        root = cursor = ((Code) objects.get("hmlBookOmega")).getText();
        add(((Code) objects.get("hmlBookAlpha")).getText());

    }


    // Adds a line at the 'cursor'
    public Text add(Text t) {
        cursor.insert(t);
        return t;
    }


    //
    // Open non-nested scope, to asynchronously write to number of linearly ordered code blocks
    //
    void openLinearScope(String label, int num, Text where) {

        Scope s = new Scope(num, label);

        // This deliberately overwrites any existing (hopefully linear...) scope of the same name
        scopeMap.put(label, s);

        for (int i = 0; i < num; i++) {

            Text open = ((Code) objects.get("hmlScopeOpen")).bind(label + i);
            where.insert(open);
            s.pointers[i] = open;

        }
    }


    public void openLinearScope(String label, int num) {

        openLinearScope(label, num, cursor);

    }


    public void openLinearScopeAtInit(String label, int num, String initLabel) {

        if (!scopeMap.containsKey(initLabel)) {
            throw new Error("openLinearScopeAtInit: Trying to open scope at initialization of unopened scope " + initLabel + ".");
        }

        Scope scope = (Scope) scopeMap.get(initLabel);

        openLinearScope(label, num, scope.pointers[0]);

    }


    public void addToLinearScope(String label, int num, Text text) {

        if (!scopeMap.containsKey(label)) {
            throw new Error("Adding to linear scope '" + label + "' without opening it.");
        }

        Scope scope = (Scope) scopeMap.get(label);

        if (scope.pointers.length <= num) {
            throw new Error("Adding to " + num + "th clique in linear scope '" + label + "' which only has " + scope.pointers.length + " blocks.");
        }

        scope.pointers[num].insert(text);
    }


    void openScopeAt(String label, Text where) {

        Text oldCursor = cursor;

        cursor = where;

        cursor.changeDepth(1);

        Text open = add(((Code) objects.get("hmlScopeOpen")).bind(label));

        Text code = add(((Code) objects.get("hmlScopeCode")).bind(label));

        Text close = add(((Code) objects.get("hmlScopeClose")).bind(label));

        cursor.changeDepth(-1);

        Scope scope = new Scope(3, label);
        scope.pointers[0] = open;
        scope.pointers[1] = close;
        scope.pointers[2] = oldCursor;

        stack.add(scope);

        scopeMap.put(label, scope);

        stackPointer += 1;

        cursor = code;
    }

    //
    // Opens a new, nested code block at cursor position, surrounded by init and exit code blocks.
    //    
    public void openScope(String label) {

        openScopeAt(label, cursor);

    }


    public void openScopeAtInit(String label, String initLabel) {

        if (!scopeMap.containsKey(initLabel)) {
            throw new Error("openScopeAtInit: Trying to open scope at initialization of unopened scope " + initLabel + ".");
        }

        Scope scope = (Scope) scopeMap.get(initLabel);

        openScopeAt(label, scope.pointers[0]);

    }


    public void closeScope(String label) {

        if (stackPointer == 0) {
            throw new Error("closeScope: stack underflow.");
        }

        Scope scope = (Scope) stack.remove(stackPointer - 1);
        if (!scope.label.equals(label)) {
            throw new Error("closeScope: trying to close scope " + label + " in scope " + scope.label);
        }

        stackPointer -= 1;

        scopeMap.remove(label);

        cursor = scope.pointers[2];

    }


    public void addInitText(String label, Text init) {

        if (!scopeMap.containsKey(label)) {
            throw new Error("addInitText: Adding to scope " + label + " without opening it.");
        }

        Scope scope = (Scope) scopeMap.get(label);

        scope.pointers[0].insert(init);

    }


    public void addExitText(String label, Text exit) {

        if (!scopeMap.containsKey(label)) {
            throw new Error("addExitText: Adding to scope " + label + " without opening it.");
        }

        Scope scope = (Scope) scopeMap.get(label);

        scope.pointers[1].insert(exit);

    }

   

    // Returns text element that serves as a reference to a scope.
    Text getScope(String label) {

        if (!scopeMap.containsKey(label)) {
            throw new Error("getScope: Scope " + label + " not found");
        }
        return ((Scope) scopeMap.get(label)).pointers[0];

    }


    String getWord(StringBuffer in, int iPos) {

        while (iPos < in.length() && (in.charAt(iPos) == ' ' || in.charAt(iPos) == '\t')) {
            iPos++;
        }
        int iEnd = iPos;
        while ((iEnd < in.length()) && (in.charAt(iEnd)) != ' ' && (in.charAt(iEnd) != '\n') && (in.charAt(iEnd) != '\t'))
        {
            iEnd++;
        }
        return in.substring(iPos, iEnd);
    }


    StringBuffer simplePreProcess(StringBuffer in, String mydefine) {

        StringBuffer processed = new StringBuffer();
        HashSet defines = new HashSet();
        String endif = new String("#endif");
        String ifndef = new String("#ifndef");
        String define = new String("#define");
        boolean preprocess = true;  // for debugging purposes only - output doesn't actually quite work

        if (define != null) {
            // Use this to choose the _HEADER_ or the _BODY_
            defines.add(mydefine);
        }

        int iPos = 0;
        int iIfLevel = 0;
        int iWriteLevel = 0;   // if lower than iIfLevel, no writing
        do {
            String iWord = getWord(in, iPos);
            int iEnd = iPos;
            while (iEnd < in.length() && in.charAt(iEnd) != '\n')
                iEnd++;
            // skip over eoln
            if (iEnd < in.length())
                iEnd++;
            if (iWord.length() > 1 && iWord.charAt(0) == '#') {
                if (iWord.compareTo(endif) == 0 && preprocess) {
                    iIfLevel--;
                    if (iWriteLevel > iIfLevel) {
                        iWriteLevel = iIfLevel;
                    }
                } else if (iWord.compareTo(define) == 0 && preprocess) {
                    if (iWriteLevel == iIfLevel) {
                        defines.add(getWord(in, iPos + 7));
                    }
                } else if (iWord.compareTo(ifndef) == 0 && preprocess) {
                    if (defines.contains(getWord(in, iPos + 7))) {
                        // no-go
                        iIfLevel++;
                    } else {
                        // okay
                        if (iWriteLevel == iIfLevel)
                            iWriteLevel++;
                        iIfLevel++;
                    }
                } else {
                    // let all (other) preprocessor lines (#include etc.) through
                    // Make them lowercase, so that we can use #DEFINE etc to bypass this preprocessor
                    if (iWriteLevel == iIfLevel) {
                        for (int i = 0; i < iWord.length(); i++) {
                            processed.append(Character.toLowerCase(iWord.charAt(i)));
                        }
                        processed.append(in.substring(iPos + iWord.length(), iEnd));
                    } else {
                        //processed.append( "// "+in.substring(iPos,iEnd) );
                    }
                }
            } else {
                if (iWriteLevel == iIfLevel) {
                    processed.append(in.substring(iPos, iEnd));
                } else {
                    //processed.append( "// "+in.substring(iPos,iEnd) );
                }
            }
            iPos = iEnd;
        } while (iPos < in.length());
        if (iIfLevel != 0) {
            System.out.println("ERROR -- Improperly nested #if and #endif statements\n");
        }

        return processed;
    }


    // Returns entire content of book
    public StringBuffer getContent(String define) {

        // create a list of text elements that can be traversed in the forward direction
        LinkedList textList = new LinkedList();
        Text t = root;
        while (t != null) {
            textList.addFirst(t);
            t = t.parent;
        }
        // add content in forward order
        StringBuffer textContent = new StringBuffer();
        Iterator i = textList.iterator();
        int depth = 0;
        while (i.hasNext()) {
            t = (Text) i.next();
            textContent.append(t.getFormattedString(depth));
            depth += t.deltaDepth();
        }
        // check indentation
        if (depth != 0) {
            System.out.println("Warning - unbalanced curly braces in output.");
        }
        // do a simple preprocessing - removing unnecessary class definitions
        textContent = simplePreProcess(textContent, define);
        // finished
        return textContent;
    }

}
	
