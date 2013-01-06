Copyright (c) 2012, Phil Gooch. 
This software is licenced under the GNU Library General Public License,
http://www.gnu.org/copyleft/gpl.html Version 3, 29 June 2007


MiniChemDrugTagger uses a small set (~500) of chemistry morphemes classified into 10 types (root, suffix, multiplier etc), and some deterministic rules based on the Wikipedia IUPAC entries, to identify chemical names, drug names and chemical formula in text.


This plugin processes Tokens and Sentences in the document, so be sure to add a Tokenizer and Sentence Splitter to your pipeline prior to adding this component.

Parameters
==========

Init-time
----------
configFileURL:	Location of configuration file that lists the morpheme lookup files
japeURL:	Location of JAPE grammar file.
minPrefixLength:	Minimum number of characters prefixing a suffix to trigger a suffix-only match


Run-time
---------
organicChemType:		Output annotation type for organic molecule mentions
inorganicChemType:		Output annotation type for inorganic molecule mentions
inputASName:			Input annotation set name
drugType:			Output annotation type for drug mentions
outputASName:			Output annotation set name
sentenceType:			Annotation type for Sentence annotations. Defaults to Sentence.
