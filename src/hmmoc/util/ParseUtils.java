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
package hmmoc.util;

import org.jdom.*;
import org.jdom.filter.*;
import java.util.*;



public class ParseUtils {
	
	public static String idId = "id$";
	public static String hmlIdId = "hmlid$";
	
	public static Element parseRoot(Document doc,String enclosingElement) {
		// Returns root element, and checks its type
		
		Element root = doc.getRootElement();
		if (!root.getName().equals(enclosingElement)) {
			throw new Error("Root element is "+root.getName()+"; expected "+enclosingElement);
		}
		return root;
	}
	
	
	public static TreeMap getMacroDefs(Element root) {
		
		TreeMap macroMap = new TreeMap();
		Iterator i = root.getDescendants( new ElementFilter("macro") );
		while (i.hasNext()) {
			Element e = (Element)i.next();
			String id = e.getAttributeValue("id");
			if (id == null) {
				throw new Error("getMacroDefs: Found <macro> element without 'id' attribute");
			}
			macroMap.put(id,e);
		}
		i = macroMap.keySet().iterator();
		while (i.hasNext()) {
			String s = (String)i.next();
			Element e = (Element)macroMap.get(s);
			//if (e.getAttributeValue("sticky")==null) {
			e.detach();
			//}
		}
		return macroMap;
	}
	
	
	public static void expandMacros(Element root, TreeMap macroMap) {
		
		Iterator i = root.getDescendants( new ElementFilter("expand") );
		List expList = new ArrayList();
		while (i.hasNext()) {
			expList.add( i.next() );
		}
		
		for (int eli=0; eli<expList.size(); eli++) {
			Element e = (Element)expList.get(eli);
			// find corresponding macro definition
			String macro = e.getAttributeValue("macro");
			if (macro == null) {
				throw new Error("expandMacros: Found <expand> element without 'macro' attribute");
			}
			if (macroMap.containsKey(macro)) {
				// make map of all slots, and their text values
				Element m = (Element)macroMap.get(macro);
				TreeMap slotMap = new TreeMap();
				Iterator si = e.getDescendants( new ElementFilter("slot") );
				while (si.hasNext()) {
					Element se = (Element)si.next();
					String var = se.getAttributeValue("var");
					if (var == null) {
						throw new Error("expandMacros: <expand macro='"+macro+"'>: Found <slot> child element without 'var' attribute");
					}
					if (var.length()<2) {
						throw new Error("expandMacros: <expand macro='"+macro+"'>: Variable '"+var+"' in <slot> is too short");
					}
					// Some library implementations (libgcj 6.0.0) don't get quoting right
					try {
						if (var.matches("[\\Q\\[].^$?{}*()|+\\E]")) {
							throw new Error("expandMacros: <expand macro='"+macro+"'>: Variable '"+var+"' in <slot> contains illegal characters: [ ] ( ) { } . \\ ^ $ ? + * | ");
						}
					} catch (Exception exc) {
						//System.out.println("Warning: Java library does not handle regular expressions correctly.");
						// ignore, hope the user behaves
					}
					String text = se.getAttributeValue("value");
					if (text == null) {
						text = se.getText();
					}
					slotMap.put(var,text);
				}
				
				// now replace macro expansion with a deep copy of the macro definition; 
				// also obtain index of macro expansion element in parent
				Parent p = e.getParent();
				if (!(p instanceof Element)) {
					throw new Error("expandMacros: <expand> found as root element, won't do.");
				}
				Element pe = (Element)p;
				int idx = pe.indexOf( e );
				e = (Element)m.clone();
				e.detach();
				
				// traverse macro, and replace any attribute of content text where any slot variable is found
				Set vars = slotMap.keySet();
				Iterator ci = e.getDescendants( new ElementFilter() );
				List elts = new ArrayList();
				while (ci.hasNext()) {
					Element ce = (Element)ci.next();
					elts.add(ce);
				}
				for (int ei=0; ei<elts.size(); ei++) {
					Element ce = (Element)elts.get(ei);
					// replace attribute content
					List attributes = ce.getAttributes();
					for (int ai = 0; ai<attributes.size(); ai++) {
						Attribute a = (Attribute)attributes.get(ai);
						Iterator ki = vars.iterator();
						while (ki.hasNext()) {
							String key = (String)ki.next();
							a.setValue( a.getValue().replaceAll( key, (String)slotMap.get(key) ) );
						}
					}
					// replace the textual content (including CDATA)
					List content = ce.getContent();
					for (int ti=0; ti<content.size(); ti++) {
						if (content.get(ti) instanceof Text) {
							Text t = (Text)content.get(ti);
							Iterator ki = vars.iterator();
							while (ki.hasNext()) {
								String key = (String)ki.next();
								t.setText( t.getText().replaceAll( key, (String)slotMap.get(key) ) );
							}
						}
					}
				}
				
				// Replace macro expansion element by the content (i.e. remove the macro itself)
				List content = new ArrayList();
				while (e.getContentSize() != 0) {
					Content c = e.getContent(0);
					c.detach();
					content.add(c);
				}
				pe.setContent(idx, content );
				
			} else {
				throw new Error("expandMacros: Found <expand> element referring to nonexistent macro ('"+macro+"')");
			}
		}
	}
	
	
	public static TreeMap parseId(Element root, TreeMap idMap) {
		
	    // Traverse document, and store all identifiers. 
	    // Assign unique identifiers to elements without identifier attribute.
	    // Allows shadowing of previous elements (but you better know what you're doing)
		int idCount = 0;
		String idstr = (idMap.size() == 0 ? hmlIdId : idId);
		TreeMap newIdMap = new TreeMap();
		Iterator i = root.getDescendants( new ElementFilter() );
		while (i.hasNext()) {
			Element e = (Element)i.next();
			String id = e.getAttributeValue("id");
			if (id == null) {
				id = idstr + idCount;
				idCount += 1;
				e.setAttribute("id",id);
			}
			if (newIdMap.containsKey(id)) {
				throw new Error("Multiple elements with identifier "+id+" encountered.");
			}
			if (idMap.containsKey(id)) {
			    // overwrite
			    String newid = "_defunct_"+id;
			    Element oldelt = (Element)idMap.get(id);
			    oldelt.setAttribute("id",newid);
			    idMap.put(newid,oldelt);
			}
			idMap.put(id,e);
			newIdMap.put(id,e);
		}
		return idMap;
	}
	
	
	public static void parseIdref(Element root, TreeMap idMap) {
		
		// Traverses document, and checks that idref's refer to same type of node
		Iterator i = root.getDescendants( new ElementFilter() );
		while (i.hasNext()) {
			Element e = (Element)i.next();
			String id = e.getAttributeValue("idref");
			if (id != null) {
				Object o = idMap.get(id);
				if (o == null) {
					throw new Error("Found <"+e.getName()+"> element (idref="+id+"), but referent not found.");
				}
				String r = ((Element)o).getName();
				if (!r.equals(e.getName())) {
					throw new Error("Found <"+e.getName()+"> element (idref="+id+") referring to <"+r+"> element.");
				}
				List l = e.getAttributes();
				if (l.size() != 2) {
					// at this point, all elements have at least an "id" attribute
					throw new Error("Found <"+e.getName()+"> element (idref="+id+") with additional attributes.");
				}
				if (e.getContentSize() != 0) {
					// perhaps this is too strict - we could allow comments and whitespace text
					throw new Error("Found <"+e.getName()+"> element (idref="+id+") with content ("+e.getContentSize()+" entries.)");
				}
				// Remove idref, and change id of this element to id of element referred to
				e.removeAttribute("idref");
				e.setAttribute("id",id);
			}
		}
	}
	
	
	public static List parseDescendants(Element root, String element, TreeMap idMap) {
		// Returns all unique id-s of descendant elements of certain type, below root
		
		Iterator i = root.getDescendants( new ElementFilter(element) );
		HashSet eltSet = new HashSet();
		while (i.hasNext()) {
			Element e = (Element)i.next();
			Element id = (Element)idMap.get(e.getAttributeValue("id"));
			eltSet.add(id);
		}
		return new ArrayList(eltSet);
	}
	
	
	public static List parseChildren(Element root, String type, TreeMap idMap) {
		// Returns all child element of certain type below root.  This function deals with references.
		
		Iterator i = root.getChildren( type ).iterator();
		ArrayList eltList = new ArrayList();
		while (i.hasNext()) {
			Element e = (Element)i.next();
			Element id = (Element)idMap.get(e.getAttributeValue("id"));   // get element referred to
			eltList.add(id);
		}
		return eltList;
	}
	
	
	public static Element parseChild(Element root, String element, TreeMap idMap) {
		// Returns child element of certain type below root
		// Throws error if child does not exist, of exists more than once
		
		List children = parseChildren(root,element,idMap);
		if (children.size() == 0) {
			throw new Error("parseChild: Element "+root.getName()+" has no child of type "+element);
		}
		if (children.size() > 1) {
			throw new Error("parseChild: Element "+root.getName()+" has more than one child of type "+element);
		}
		return (Element)children.get(0);
	}
	
	
	public static Element parseAttribute(Element elem, String attrname, String elemref, TreeMap idMap) {
		// Retrieves attribute from element, and returns element it refers to.
		// Checks whether attribute exists; and whether it refers to an element of the appropriate type
		
		String attr = elem.getAttributeValue(attrname);
		String id = elem.getAttributeValue("id");
		if (attr == null) {
			throw new Error("Couldn't find required attribute \""+attrname+"\" referring to <"+elemref+"> on element <"+elem.getName()+"> (id="+id+")");
		}
		if (!idMap.containsKey(attr)) {
			throw new Error("Attribute \'"+attrname+"\' on element <"+elem.getName()+"> (id='"+id+"') has value '"+attr+"', but no such identifier (of type <"+elemref+">) found");
		}
		Element e = (Element)idMap.get(attr);
		if (!e.getName().equals(elemref)) {
			throw new Error("Attribute \""+attrname+"\" on element <"+elem.getName()+"> (id="+id+") with value "+attr+" refers to <"+e.getName()+">, but <"+elemref+"> required");
		}
		return e;
	}
	
	
	public static Element parseAttributeWithDefault(Element elem, String attrname, String elemref, TreeMap idMap) {
		// Retrieves attribute from element, and returns element it refers to.
		// Checks whether attribute exists; and whether it refers to an element of the appropriate type
		
		String attr = elem.getAttributeValue(attrname);
		if (attr == null) {
			return null;
		}
		return parseAttribute(elem, attrname, elemref, idMap);
	}
	
	
	
	public static List parseMultiAttribute(Element elem, String attrname, String elemref, TreeMap idMap) {
		// Retrieves attribute from element, and returns list of elements it refers to (may be null, if attribute is "" or absent)
		// Checks whether attribute refers to an element of the appropriate type
		
		String attr = elem.getAttributeValue(attrname);
		ArrayList attrList = new ArrayList(0);
		if (attr == null) {
			return attrList;
		}
		int i=0;
		while (i<attr.length() && (attr.charAt(i)==' ' || attr.charAt(i)=='\t'))
			i++;
		int j=i;
		while (i<attr.length() && (attr.charAt(i)!=' ' && attr.charAt(i)!='\t'))
			i++;
		if (i>j) {
			if (!idMap.containsKey(attr.substring(j,i))) {
				throw new Error("Attribute \""+attrname+"\" on element <"+elem.getName()+"> contains value "+attr.substring(j,i)+", but no such identifier found");
			}
			Element e = (Element)idMap.get(attr.substring(j,i));
			if (!e.getName().equals(elemref)) {
				throw new Error("Attribute \""+attrname+"\" on element <"+elem.getName()+"> containing value "+attr.substring(j,i)+
						" refers to <"+e.getName()+">, but <"+elemref+"> required");
			}
			attrList.add( e );
		}
		return attrList;
	}
	
	
	public static String getText(Element elem) {
		// Returns text held in elem, or value of "value" attribute if elem has one.
		// If element has both text and "value" attribute, returns error.
		
		String text1 = elem.getTextTrim();
		String text2 = elem.getAttributeValue("value");
		if (text2==null) return text1;                    // returns "" if no element has no text
		if (text1.equals("")) return text2;
		throw new Error("Element "+elem.getName()+" has both 'text' attribute and text.\n(Attribute value: '"+text2+"')\n(Text: '"+text1+"')");
	}
	
	
	public static String getTextNotrim(Element elem) {
		// Returns text held in elem, or value of "value" attribute if elem has one.
		// If element has both text and "value" attribute, returns error.
		
		String text1 = elem.getText();
		String text2 = elem.getAttributeValue("value");
		if (text2==null) return text1;                    // returns "" if no element has no text
		if (text1.equals("")) return text2;
		throw new Error("Element "+elem.getName()+" has both 'text' attribute and text.\n(Attribute value: '"+text2+"')\n(Text: '"+text1+"')");
	}
	
	
	public void descend(Element e, String prefix) {
		
		List l = e.getContent();
		Iterator i = l.iterator();
		while (i.hasNext()) {
			
			Object o = i.next();
			if (o instanceof Element) {
				System.out.println( prefix + "Element: " + ((Element) o).getName() );
				descend( (Element)o, prefix + "  " );
			} else if (o instanceof Text) {
				String t = ((Text) o).getTextTrim();
				if (t.length() != 0) {
					System.out.println( prefix + "Text: " + t );
				}
			} else if (o instanceof EntityRef) {
				System.out.println( prefix + "EntityRef: " + ((EntityRef) o).getName() + " ID: "+((EntityRef) o).getPublicID() );
			} else if (o instanceof Comment) {
				System.out.println( prefix + "Comment: " + ((Comment) o).getText() );
			} else {
				System.out.println( prefix + "Something..." );
			}
		}
	}
	
	
	
}
