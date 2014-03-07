#
# Simple parser/converter for HMMER hmm files
#
# This script contains 3 main subroutines:
#  - parsehmm    : parses a HMMER "plan-7" hmm file
#  - buildhmm    : generates an abstract HMM from HMMER "plan-7" data 
#  - generatexml : generates XML input for HMMoC, from an abstract HMM description
#
# Gerton Lunter,  25/6/2007
#

import sys,re


# splits line into words
def parseline( line ):
    return [w for w in re.split('[^-*.:;,!/<>@_a-zA-Z0-9]+',line[:-1]) if w != '']


# calculate probabilities from a HMMER bitscore line
def parseprobs( probs, null ):

    if type(null) != type([]):
        null = [ null for x in probs ]

    def parsenum(x):
        INTSCALE = 1000.0
        if x == "*":
            return 0.0
        return pow(2.0,int(x)/INTSCALE)
    
    return [ parsenum(probs[i]) * null[i] for i in range(len(probs)) ]


# check & renormalize subvector
def renorm( probs, indices ):
    tot = 0
    for i in indices:
        tot += probs[i]
    if abs(tot - 1.0) > 1.0e-3:
        #print "Warning: probabilities normalize to %s" % tot
	pass
    for i in indices:
        probs[i] /= tot
        

def parsehmm( infile ):

    line = infile.readline()

    # check for identifier
    id = line[:-1].split(' ')[0]
    if id != "HMMER2.0":
        print "Found identifier '%s', expected 'HMMER2.0' -- is this a HMMER file?" % id
        sys.exit(1)

    # now read various (and ignore more) tag lines, until an HMM tag is found
    tags = {'NAME':None, 'LENG':None, 'XT':None, 'NULT':None, 'NULE':None, 'MAP':None, 'ALPH':None}
    tag = None
    while tag != "HMM":
        if tag in tags:
            if tags[tag]:
                print "Tag %s found twice -- corrupted HMMER file?" % tag
                sys.exit(1)
            tags[tag] = data
        tag, data = (lambda x: (x[0],x[1:]))( parseline( infile.readline() ) )
        
    # store alphabet - listed after HMM tag
    alphabet = data

    # convert special probabilities and null distributions into numbers
    prob_xt = parseprobs( tags['XT'], 1.0 )
    prob_nult = parseprobs( tags['NULT'], 1.0 )
    prob_nule = parseprobs( tags['NULE'], 1.0 / len(tags['NULE']) )

    renorm( prob_xt, [0,1] )     # n->b,n
    renorm( prob_xt, [2,3] )     # e->c,j
    renorm( prob_xt, [4,5] )     # c->t,c
    renorm( prob_xt, [6,7] )     # j->b,j
    renorm( prob_nult, [0,1] )
    renorm( prob_nule, range(len(prob_nule)) )

    # ignore transition identifier line
    infile.readline()

    # read b->m, b->i and b->d transitions (defines the b->d transition - redundantly)
    bmid = parseline( infile.readline() )
    prob_bmid = parseprobs( bmid, 1.0 )
    renorm( prob_bmid, [0,1,2] )

    # now, read the M*3 data lines describing the model
    leng = int(tags['LENG'][0])
    tags['profile'] = []
    for m in range(leng):
        line1 = parseline( infile.readline() )
        line2 = parseline( infile.readline() )
        line3 = parseline( infile.readline() )

        # check node number
        if int(line1[0]) != m+1:
            print "Node %s: expected node number; found %s" % (m+1, line1[0])
            sys.exit(1)

        # read column number
        if tags['MAP'] and tags['MAP'][0].lower() == 'yes':
            colnumber = int(line1[-1])
        else:
            colnumber = m+1

        prob_match = parseprobs( line1[1:len(alphabet)+1], prob_nule )
        prob_insert = parseprobs( line2[1:], prob_nule )
        prob_trans = parseprobs( line3[1:], 1.0 )

        renorm( prob_match, range(len(alphabet)) )
        renorm( prob_trans, [0,1,2,8] )                   # m->m, i, d, e
        if m != leng-1:
            renorm( prob_trans, [3,4] )                   # i->m, i
            renorm( prob_trans, [5,6] )                   # d->m, d
            renorm( prob_insert, range(len(alphabet)) )

        # store data
        tags['profile'].append( (colnumber, prob_match, prob_insert, prob_trans) )

    # store more data
    tags['btod'] = prob_bmid[2]
    tags['alphabet'] = alphabet
    tags['NAME'] = ' '.join(tags['NAME'])
    tags['ALPH'] = tags['ALPH'][0]
    tags['LENG'] = leng
    tags['XT'] = prob_xt
    tags['NULT'] = prob_nult
    tags['NULE'] = prob_nule

    # consistency check - might as well exploit redundancy (0th node; 3rd data element; 7th transition = b->m)
    if abs( prob_bmid[0] - tags['profile'][0][3][7] ) > 1.0e-3:
        print "Warning: Inconsistent initial b->m transition: %s and %s" % (prob_bmid[0], tags['profile'][0][3][7])

    return tags


#
# purge zero-probability transitions
#
def purgetransitions( ttab, epsilon = 1.0e-6 ):
    return [ t for t in ttab if abs(t[2])>epsilon ]


#
# Builds an HMM from HMMER plan7 data
#
# HMMER uses both Mealy and Moore states - so allow transitions to emit (only n->n and c->c),
# and create a table for emitting states as well
#
# Returns: (alphabet, transition_table, emission_table)
#          alphabet = [char1, char2, .... ]
#          transition_table = [ (from_state, to_state, prob, emission), ... ]
#          emission_table = [ (state, emission) ]
#          emission = None or [prob_char1, prob_char2, ...]
def buildhmm( plan7 ):

    alphabet = plan7['alphabet']
    ttab = []
    etab = []

    # first build main profile, transitions from states 1 to leng-1
    leng = plan7['LENG']
    for m in range(leng-1):
        
        colnumber, prob_match, prob_insert, prob_trans = plan7['profile'][m]
        nextcolnumber = plan7['profile'][m+1][0]

        curm = "M%03d" % colnumber
        nextm = "M%03d" % nextcolnumber
        curi = "I%03d" % colnumber
        curd = "D%03d" % colnumber
        nextd = "D%03d" % nextcolnumber
        
        #m->m   m->i   m->d   i->m   i->i   d->m   d->d   b->m   m->e
        mm, mi, md, im, ii, dm, dd, bm, me = range(9)
        
        ttab.append( [curm, nextm, prob_trans[mm]] )
        ttab.append( [curm, curi, prob_trans[mi]] )
        ttab.append( [curm, "endprofile", prob_trans[me] ] )
        ttab.append( [curi, nextm, prob_trans[im] ] )
        ttab.append( [curi, curi, prob_trans[ii] ] )
        ttab.append( [curd, nextm, prob_trans[dm] ] )
        if m != leng-2:
            # hack to remove silent loop....
            # Proper 'wing retraction' is much more expensive than is implemented in HMMER...
            ttab.append( [curm, nextd, prob_trans[md] ] )
            ttab.append( [curd, nextd, prob_trans[dd] ] )

        ttab.append( ["beginprofile", curm, prob_trans[bm] ] )
        if m == 0:
            # assume that the profile has length > 1
            ttab.append( ["beginprofile", curd, plan7['btod'] ] )

        etab.append( (curm, prob_match) )
        etab.append( (curi, prob_insert ) )

    # add the final node of the main profile
    curm = nextm
    curd = nextd
    colnumber, prob_match, prob_insert, prob_trans = plan7['profile'][leng-1]

    ttab.append( [curm, "endprofile", prob_trans[me] ] )
    #ttab.append( [curd, "endprofile", 1.0 ] )
    ttab.append( ["beginprofile", curm, prob_trans[bm] ] )

    etab.append( (curm, prob_match) )

    # build the rest of the HMM
    xt = plan7['XT']
    ttab.append( [ "start", "nterminal", 1.0] )
    ttab.append( [ "nterminal", "beginprofile", xt[0]] )
    ttab.append( [ "nterminal", "nterminal", xt[1], plan7['NULE']] )
    ttab.append( [ "endprofile", "cterminal", xt[2]] )
    ttab.append( [ "endprofile", "join", xt[3]] )
    ttab.append( [ "cterminal", "end", xt[4]] )
    ttab.append( [ "cterminal", "cterminal", xt[5], plan7['NULE']] )
    ttab.append( [ "join", "beginprofile", xt[6]] )
    ttab.append( [ "join", "join", xt[7], plan7['NULE']] )

    # purge any zero-probability transitions
    ttab = purgetransitions(ttab)
    
    # done
    return (alphabet, ttab, etab)


#
# From an HMM description, generate an (ordered) list of states, and associated emissions
#
# Assumes that start/end states are non-emitting and have their canonical name
#
def getstates( hmm ):

    alphabet, ttab, etab = hmm
    states = {}
    for t in ttab:
        states[t[0]] = None
        states[t[1]] = None
    for e in etab:
        states[e[0]] = e[1]
    del states['start']
    del states['end']
    output = [ [s, states[s]] for s in states ]
    output.sort()
    return [['start',None]] + output + [['end',None]]
        


def generatexml( hmm, basename ):

    emitid = "emit_%s"
    emitid2 = "emit_%s_to_%s"
    transid = "tr_%s_%s"

    states = getstates(hmm)
    alphabet, ttab, etab = hmm

    # find all emitting states, and give them numerical and textual identifiers
    emissions = {}
    for s in states:
        if s[1]:
            ident = emitid % s[0]
            emissions[ ident ] = ( len(emissions), s[1] )
            s[1] = ident

    # find all emitting transitions
    for i in range(len(ttab)):
        t = ttab[i]
        if len(t)==4 and t[3]:
            ident = emitid2 % (t[0],t[1])
            emissions[ ident ] = ( len(emissions), t[3] )
            t[3] = ident
        else:
            ttab[i] = [t[0],t[1],t[2],None]

    # make identifiers for all transitions
    transitions = {}
    for i in range(len(ttab)):
        ident = transid % (ttab[i][0], ttab[i][1])
        transitions[ident] = len(transitions)
        ttab[i].append( ident )

    # find all non-emitting transitions that point to non-emitting states
    emptytransitions = {}
    for t in ttab:
	if (not t[3]) and not ((emitid % t[1]) in emissions):
	   emptytransitions[ t[-1] ] = None

    #####################################
    #
    # Build components for the XML file
    #
    #####################################

    #
    # make definitions block for identifiers
    #
    definitions = ''.join(["#DEFINE %s %s\n" % (ident,emissions[ident][0]) for ident in emissions])
    definitions += ''.join(["#DEFINE %s %s\n" % (ident,transitions[ident]) for ident in transitions])
    definitions = "\n<!-- Definitions to refer to array indices by name, rather than number -->\n" + \
                  "<code id=\"definitions\" type=\"statement\"><![CDATA[\n%s]]></code>" % definitions
    

    #
    # make initializing block
    #
    init = """
<code id="initheader" type="statement" where="includes"><![CDATA[
    #include <vector>
    #include "%s_params.h"
]]></code>

<code id="initialise" type="statement" init="initheader">
    <![CDATA[
""" % basename
    
    init += "int iLen1 = iSequence1.size();\n"
    init += "int iTranslate[256];\n"
    init += "for(int i=0;i<256;i++) iTranslate[i]=0;\n"
    for i in range(len(alphabet)):
        init += "iTranslate[(int)'%s'] = %s;\n" % (alphabet[i],i)
    init += "vector<int> iTranslatedSequence1;\n"
    init += "for(int i=0;i<(int)iSequence1.size();i++) iTranslatedSequence1.push_back(iTranslate[(int)iSequence1[i]]);\n"
    init += "]]>\n</code>\n\n"

    #
    # make parameter initialization block
    #
    parinit = "<code id=\"parinit\" type=\"statement\"><![CDATA[\n"
    parinit += "#include \"%s_params.h\"\n" % basename
    parinit += "#include \"%s.h\"\n" % basename
    parinit += "hmmer_hmmShortReal iT[%s];\n" % len(transitions)
    parinit += "hmmer_hmmShortReal iE[%s][%s];\n" % (len(emissions), len(alphabet))
    parinit += "void initpars(){\n"
    for t in ttab:
        parinit += "iT[%s] = %s;\n" % (t[-1], t[2])
    for e in emissions:
        for j in range(len(alphabet)):
            parinit += "iE[%s][%s] = %s;\n" % (e, j, emissions[e][1][j])
    parinit += "}\n"
    parinit += "]]></code>\n"

    #
    # make emission tags
    #
    emissiontags = """
  <emission id="empty">
    <probability><code type="expression"> 1.0 </code></probability>
  </emission>"""

    emissiontag ="""
  <emission id="%s"><output idref="sequence1"/><probability><code><identifier output="sequence1" value="iSymbol"/>
    iE[%s][iSymbol]
  </code></probability></emission>
"""

    for e in emissions:
        # this is ugly : same ID is used for #define and xml
        emissiontags += emissiontag % (e,e)

    #
    # make probability tags
    #
    probtags = ""
    probtag = "  <probability id=\"%s\"><code type=\"expression\" init=\"initialise\"> iT[%s] </code></probability>\n"
    for t in ttab:
        probtags += probtag % ("pr_"+t[-1], t[-1])

    #
    # make transition tags
    #
    transitiontags = "\n  <transitions>\n"
    transitiontag = "    <transition from=%s to=%s probability=%s %s id=%s/>\n"
    for t in ttab:
        if t[3]:
	   emissiontag = "emission=\"%s\"" % t[3]
	else:
	   if t[-1] in emptytransitions:
	      emissiontag = "emission=\"empty\""
	   else:
	      emissiontag = ""
        transitiontags += transitiontag % ("%-15s" % ('"'+t[0]+'"'),
                                           "%-15s" % ('"'+t[1]+'"'),
                                           "%-25s" % ('"pr_'+t[-1]+'"'),
					   emissiontag,
                                           "%-25s" % ('"'+t[-1]+'"') )
    transitiontags += "  </transitions>\n\n"

    #
    # make main clique (omit 'start' and 'end')
    #
    statetags = ""
    statetag = "    <state id=\"%s\" %s/>\n"
    for s in states[1:-1]:
        if s[1]:
	   # emitting state
	   emissiontag = " emission=\"%s\"" % s[1]
	else:
	   # nonemitting state -- transition takes care of the 'empty' tag
	   emissiontag = ""
	statetags += statetag % (s[0],emissiontag)

    statetags = "  <clique id=\"block2\">\n" + statetags + "  </clique>\n"

    #
    # make alphabet tag
    #
    alphabettag = "<alphabet id=\"hmmer_alphabet\"> %s </alphabet>\n" % (' '.join(alphabet))

    ############################
    #
    # Put it all together
    #
    ############################

    xml = "<?xml version=\"1.0\"?>\n"
    xml += "<hml>\n"
    xml += alphabettag

    xml += """
<output id="sequence1">
      <alphabet idref="hmmer_alphabet"/>
      <identifier type="sequence" value="iTranslatedSequence1"/>
      <identifier type="length" value="iLen1"/>
      <code type="parameter">
        <![CDATA[
          vector<char>& iSequence1
        ]]>
      </code>
      <code type="parameter"> hmmer_hmmShortReal iT[] </code>
      <code type="parameter"> hmmer_hmmShortReal iE[][20] </code>
</output>
"""

    xml += init

    xml += parinit

    xml += definitions

    xml += """

<hmm id="hmmer_hmm">

  <description>  Profile HMM generated from HMMER file   </description>

  <outputs>
    <!-- Define number of emission 'tapes', and their alphabets -->
    <output idref="sequence1"/>
  </outputs>

  <clique id="block1">
    <state id="start"/>
  </clique>

  <clique id="block3">
    <state id="end"/>
  </clique>

"""

    xml += statetags

    xml += """
  <graph>
    <clique idref="block1"/>
    <clique idref="block2"/>
    <clique idref="block3"/>
  </graph>
"""

    xml += emissiontags

    xml += transitiontags

    xml += probtags

    # close <hmm> tag
    xml += "</hmm>\n"

    xml += """

<!-- Code generation -->

<forward outputTable="yes" baumWelch="no" cacheValues="yes" name="Forward" id="forward">
  <hmm idref="hmmer_hmm"/>
</forward>


<viterbi name="Viterbi" cacheValues="yes" id="viterbi">
  <hmm idref="hmmer_hmm"/>
</viterbi>


<codeGeneration realtype="%s" file="%s.cc" header="%s.h" language="C++">
  <forward idref="forward"/>
  <viterbi idref="viterbi"/>
</codeGeneration>


<codeGeneration file="%s_params.cc" language="C++">
  <code idref="parinit"/>
</codeGeneration>


<codeGeneration file="%s_params.h" language="C++">
  <code idref="definitions"/>
</codeGeneration>


</hml>


""" % (realtype,
       basename,
       basename,
       basename,
       basename)


    print xml

    



#############################################################################################################
#
# Main code
#
#############################################################################################################



if len(sys.argv) == 1:
    print "Translates HMMER .hmm file into into HMMoC .xml file\n\n"
    print "Usage: %s [--logspace] filename.hmm" % sys.argv[0]
    sys.exit(1)

realtype = "bfloat"
if len(sys.argv) == 3:
   filename = sys.argv[2]
   if sys.argv[1] == "--logspace":
      realtype = "logspace"
   else:
      print "I don't understand '%s' -- sorry" % sys.argv[1]
else:
   filename = sys.argv[1]

infile = open(filename)

plan7 = parsehmm( infile )

hmm = buildhmm( plan7 )

generatexml( hmm, filename.split('.')[0] )



