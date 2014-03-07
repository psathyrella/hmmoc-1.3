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


import java.util.*;


public class Graph {

    // Returns list of nodes in graph.
    // Graphs are represented as collections containing elements of type Edge.
    public static List nodes( Collection g ) {

        HashSet nodes = new HashSet();
        Iterator i = g.iterator();
        while (i.hasNext()) {
            Edge t = (Edge)i.next();
            nodes.add( t.from );
            nodes.add( t.to );
        }
        return new ArrayList( nodes );
    }


    // Returns reversed graph
    static List reverse( Collection g ) {

        ArrayList rev = new ArrayList();
        Iterator i = g.iterator();
        while (i.hasNext()) {
            Edge t = (Edge)i.next();
            rev.add( new Edge( t.to, t.from ) );
        }
        return rev;
    }


    // Returns number of connected components
    public static int connectedComponents( Collection g ) {

        TreeMap c = new TreeMap();
        List nl = nodes( g );
        Iterator i = nl.iterator();
        int numComponents = nl.size();
        while (i.hasNext()) {
            Object n = i.next();
            ArrayList connComp = new ArrayList();
            connComp.add( n );
            c.put( n, connComp );
        }
        i = g.iterator();
        while (i.hasNext()) {
            Edge t = (Edge)i.next();
            Object n1 = t.from;
            Object n2 = t.to;
            if (c.get(n1) != c.get(n2)) {
                ArrayList newCC = ((ArrayList)c.get(n1));
                newCC.addAll( (ArrayList)c.get(n2) );
                Iterator j = newCC.iterator();
                while (j.hasNext()) {
                    Object n = j.next();
                    c.put( n, newCC );
                }
                numComponents -= 1;
            }
        }
        return numComponents;
    }


    // Returns all nodes reachable from o
    static Set reachable( Collection g, Object o ) {

        Set r = reachableOnce( g, o );
        int num = 0;
        int oldNum;
        do {
            oldNum = num;
            Iterator i = r.iterator();
            HashSet toAdd = new HashSet();
            while (i.hasNext()) {
                toAdd.addAll( reachableOnce( g, i.next() ) );
            }
            r.addAll(toAdd);
            num = r.size();
        } while (num > oldNum );
        return r;
    }


    static Set reachableOnce( Collection g, Object o ) {

        HashSet r = new HashSet();
        Iterator i = g.iterator();
        while (i.hasNext()) {
            Edge e = (Edge)i.next();
            if (e.from == o) {
                r.add( e.to );
            }
        }
        return r;
    }


    // Returns all nodes that can reach o
    static Set reaching( Collection g, Object o ) {

        Set r = reachingOnce( g, o );
        int num = 0;
        int oldNum;
        do {
            oldNum = num;
            Iterator i = r.iterator();
            HashSet toAdd = new HashSet();
            while (i.hasNext()) {
                toAdd.addAll( reachingOnce( g, i.next() ) );
            }
            r.addAll(toAdd);
            num = r.size();
        } while (num > oldNum );
        return r;
    }


    static Set reachingOnce( Collection g, Object o ) {

        HashSet r = new HashSet();
        Iterator i = g.iterator();
        while (i.hasNext()) {
            Edge e = (Edge)i.next();
            if (e.to == o) {
                r.add( e.from );
            }
        }
        return r;
    }


    // Returns all nodes in the cylic component containing o
    public static Set cyclicComponent( Collection g, Object o ) {

        Set one = reachable( g, o );
        Set two = reaching( g, o );
        Set three = new HashSet();
        three.addAll(one);
        three.removeAll(two);
        Set four = new HashSet();
        four.addAll(two);
        four.removeAll(one);
        Set five = new HashSet();
        five.addAll(one);
        five.addAll(two);
        five.removeAll(three);
        five.removeAll(four);
        return five;
    }


    // Removes from graph all edges involving edge o
    static Collection removeNodeEdges( Collection g, Object o )
    {
        Collection l = new ArrayList();
        Iterator i = g.iterator();
        while (i.hasNext()) {
            Edge n = (Edge)i.next();
            if ((n.to != o) && (n.from != o)) {
                l.add( n );
            }
        }
        return l;
    }


    // Returns infimum of graph, i.e. all non-referenced nodes
    public static List infimum( Collection g )
    {
        ArrayList ll = new ArrayList();
        List nl = nodes( g );
        Iterator i = nl.iterator();
        while (i.hasNext()) {
            Object o = i.next();
            if (reachingOnce(g,o).size() == 0) {
                ll.add(o);
            }
        }
        return ll;
    }


    // Returns supremum of graph, i.e. all nodes that reference no other nodes
    public static List supremum( Collection g )
    {
        ArrayList ll = new ArrayList();
        List nl = nodes( g );
        Iterator i = nl.iterator();
        while (i.hasNext()) {
            Object o = i.next();
            if (reachableOnce(g,o).size() == 0) {
                ll.add(o);
            }
        }
        return ll;
    }


    // Sort graph into ascending cyclic components
    // Returns a list of transient nodes, and lists of nodes
    //  forming a cyclic component.
    // (Self reference nodes appear in singlet lists.)
    public static List sortGraph( Collection g )
    {
        ArrayList ll = new ArrayList();
        List nl = nodes( g );
        Collection gg = g;

        while (nl.size()>0) {
            Object o = null;
            boolean found = false;
            // First try to get "start" nodes
            Iterator i = nl.iterator();
            while (!found && i.hasNext()) {
                o = i.next();
                if (reachingOnce(gg,o).size() == 0) {
                    ll.add( o );
                    found = true;
                }
            }
            // If not successful, try to find "start"
            // cyclic components
            i = nl.iterator();
            while (!found && i.hasNext()) {
                o = i.next();
                Set c = cyclicComponent(gg,o);
                if (c.size() == reaching(gg,o).size()) {
                    ll.add( new ArrayList( c ) );
                    found = true;
                    o = c;
                }
            }
            // (Something was found - has to be...)
            if (o instanceof Set) {
                nl.removeAll( (Set)o );
                Iterator ri = ((Set)o).iterator();
                while (ri.hasNext()) {
                    gg = removeNodeEdges( gg, ri.next() );
                }
            } else {
                nl.remove( o );
                gg = removeNodeEdges( gg, o );
            }
        }
        return ll;
    }
}
