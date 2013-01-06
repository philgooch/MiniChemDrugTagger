/*
 *  Copyright (c) 2012, Phil Gooch.
 *
 *  This software is licenced under the GNU Library General Public License,
 *  http://www.gnu.org/copyleft/gpl.html Version 3, 29 June 2007
 *
 *  Phil Gooch 04/2012
*/

package org.philgooch;

import gate.*;
import gate.creole.*;
import gate.creole.metadata.*;
import gate.util.*;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.event.ProgressListener;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;
import java.io.*;
import java.net.*;

/**
 *
 * @author philipgooch
 */
@CreoleResource(name = "Mini Chemical and Drug Tagger",
helpURL = "",
comment = "Uses a minimal morpheme set and rules to identify IUPAC-like chemical and drug names in text.")
public class MiniChemDrugTagger extends AbstractLanguageAnalyser implements ProgressListener,
        ProcessingResource,
        Serializable {

    private String inputASName;     //  Input AnnotationSet name
    private String outputASName;    // Output AnnotationSet set name
    private String drugType;                   // Annotation for drug mentions
    private String organicChemType;         // Annotation for organic chemicals
    private String inorganicChemType;       // Annotation for inorganic chemicals
    private String formulaType;       // Annotation for chemical symbolic formulae
    
    private Integer minPrefixLength;    // minimum overall length of string before a suffix
    private URL configFileURL;      // URL to configuration file that defines suffixes and key words
    private String sentenceType;               // default to Sentence
    // Map to contain the regex Patterns morpheme lookups
    private Map<String, Pattern> patternMap;
    // Exit gracefully if exception caught on init()
    private boolean gracefulExit;
    private Transducer japeTransducer = null;     // JAPE to clean up the output
    private URL japeURL;      // URL to JAPE main file


    /**
     *
     * @param key
     * @param options
     */
    private void addPrefixPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("\\b(" + option + ")", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }


    /**
     *
     * @param key
     * @param options
     */
    private void addSuffixPluralPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("\\b(\\w{" + minPrefixLength + ",})(" + option + ")s?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }

    

    // Chemical elements and compounds
    private void addElementNamePattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("(" + option + ")\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }

    private void addRootPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile(option, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }

    private void addPluralRootPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("(" + option + ")s?", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }

    private void addChemWordPluralPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("\\b?(" + option + ")s?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }

    private void addChemSuffixPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("(" + option + ")\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }

    private void addChemSymbolPattern(String key, HashMap<String, String> options) {
        String option = options.get(key);
        if (option != null) {
            patternMap.put(key, Pattern.compile("(" + option + "){2,}"));
        }
    }

    @Override
    public Resource init() throws ResourceInstantiationException {
        gracefulExit = false;

        if (configFileURL == null) {
            gracefulExit = true;
            gate.util.Err.println("No configuration file provided!");
        }

        if (japeURL == null) {
            gracefulExit = true;
            gate.util.Err.println("No JAPE grammar file provided!");
        }

        // create the init params for the JAPE transducer
        FeatureMap params = Factory.newFeatureMap();
        params.put(Transducer.TRANSD_GRAMMAR_URL_PARAMETER_NAME, japeURL);
        // Code borrowed from Mark Greenwood's Measurements PR
        if (japeTransducer == null) {
            // if this is the first time we are running init then actually create a
            // new transducer as we don't already have one
            FeatureMap hidden = Factory.newFeatureMap();
            Gate.setHiddenAttribute(hidden, true);
            japeTransducer = (Transducer) Factory.createResource("gate.creole.Transducer", params, hidden);
        } else {
            // we are being run through a call to reInit so simply re-init the
            // underlying JAPE transducer
            japeTransducer.setParameterValues(params);
            japeTransducer.reInit();
        }

        ConfigReader config = new ConfigReader(configFileURL);
        gracefulExit = config.config();

        try {
            HashMap<String, String> options = config.getOptions();

            patternMap = new HashMap<String, Pattern>();

            // Special handling for element symbols and formulae
            // patternMap.put("chemistry_symbol", Pattern.compile("(\\d|\\(|\\)|\\[|\\]|=|\\+|\\-|\\u2212|\\u00B7|Zr|Zn|Yb|Y|Xe|W|V|Uuu|Uut|Uus|Uuq|Uup|Uuo|Uun|Uuh|Uub|U|Tm|Tl|Ti|Th|Te|Tc|Tb|Ta|Sr|Sn|Sm|Si|Sg|Se|Sc|Sb|S|Ru|Rn|Rh|Rf|Re|Rb|Ra|Pu|Pt|Pr|Po|Pm|Pd|Pb|Pa|P|Os|O|Np|No|Ni|Ne|Nd|Nb|Na|N|Mt|Mo|Mn|Mg|Me|Md|Lu|Lr|Li|La|Kr|K|Ir|In|I|Hs|Ho|Hg|Hf|He|H|Ge|Gd|Ga|Fr|Fm|Fe|F|Eu|Et|Es|Er|Dy|Ds|Db|Cu|Cs|Cr|Co|Cn|Cm|Cl|Cf|Ce|Cd|Ca|C|Br|Bk|Bi|Bh|Be|Ba|B|Au|At|As|Ar|Am|Al|Ag|Ac){2,}"));

            // patterns from lookup lists
            addChemSymbolPattern("chemistry_symbol", options);
            addSuffixPluralPattern("drug_suffix", options);
            addRootPattern("chemistry_alkane_root", options);
            addRootPattern("chemistry_ion_root", options);
            // alkane suffix can occur in the middle of a word, e.g. methylphenyl-4-ala...
            addPluralRootPattern("chemistry_alkane_suffix", options);
            addPrefixPattern("chemistry_oxidation_prefix", options);
            addChemSuffixPattern("chemistry_oxidation_suffix", options);
            addPrefixPattern("chemistry_structure_prefix", options);

            addElementNamePattern("chemistry_element_name", options);
            addChemSuffixPattern("chemistry_inorganic_compound", options);
            addChemWordPluralPattern("chemistry_amino_acid", options);
            addRootPattern("chemistry_multiplier", options);
            
        } catch (NullPointerException ne) {
            gracefulExit = true;
            gate.util.Err.println("Missing or unset configuration options. Please check configuration file.");
        }

        return this;
    } // end init()

    @Override
    public void execute() throws ExecutionException {
        interrupted = false;

        // quit if setup failed
        if (gracefulExit) {
            gracefulExit("Plugin was not initialised correctly. Exiting gracefully ... ");
            return;
        }

        AnnotationSet inputAS = (inputASName == null || inputASName.trim().length() == 0) ? document.getAnnotations() : document.getAnnotations(inputASName);
        AnnotationSet outputAS = (outputASName == null || outputASName.trim().length() == 0) ? document.getAnnotations() : document.getAnnotations(outputASName);


        AnnotationSet sentenceAS = null;
        if (sentenceType != null && !sentenceType.isEmpty()) {
            sentenceAS = inputAS.get(sentenceType);
        }

        // Document content
        String docContent = document.getContent().toString();
        int docLen = docContent.length();

        // For matching purposes replace all whitespace characters with a single space
        docContent = docContent.replaceAll("[\\s\\xA0\\u2007\\u202F]", " ");

        fireStatusChanged("Locating chemical and drug mentions in " + document.getName());
        fireProgressChanged(0);

        if (sentenceAS != null) {
            for (Annotation sentence : sentenceAS) {
                Long sentStartOffset = sentence.getStartNode().getOffset();
                Long sentEndOffset = sentence.getEndNode().getOffset();
                String sentenceContent = docContent.substring(sentStartOffset.intValue(), sentEndOffset.intValue());

                doFormulaMatch(patternMap.get("chemistry_symbol"), sentenceContent, inputAS, outputAS, sentStartOffset, docLen);
                doMatch(patternMap.get("drug_suffix"), sentenceContent, inputAS, outputAS, "tmpDrug", sentStartOffset, docLen);
                doMatch(patternMap.get("chemistry_alkane_root"), sentenceContent, inputAS, outputAS, "tmpAlkaneRoot", sentStartOffset, docLen);
                doMatch(patternMap.get("chemistry_ion_root"), sentenceContent, inputAS, outputAS, "tmpIonRoot", sentStartOffset, docLen);
                doMatch(patternMap.get("chemistry_alkane_suffix"), sentenceContent, inputAS, outputAS, "tmpAlkaneSuffix", sentStartOffset, docLen);
                doMatch(patternMap.get("chemistry_oxidation_prefix"), sentenceContent, inputAS, outputAS, "tmpOxidationPrefix", sentStartOffset, docLen);
                doMatch(patternMap.get("chemistry_oxidation_suffix"), sentenceContent, inputAS, outputAS, "tmpOxidationSuffix", sentStartOffset, docLen);
                doMatch(patternMap.get("chemistry_element_name"), sentenceContent, inputAS, outputAS, "tmpElement", sentStartOffset, docLen);
                doMatch(patternMap.get("chemistry_inorganic_compound"), sentenceContent, inputAS, outputAS, "tmpInorganicCompound", sentStartOffset, docLen);
                doMatch(patternMap.get("chemistry_amino_acid"), sentenceContent, inputAS, outputAS, "tmpAminoAcid", sentStartOffset, docLen);
                doMatch(patternMap.get("chemistry_multiplier"), sentenceContent, inputAS, outputAS, "tmpMultiplier", sentStartOffset, docLen);
                doMatch(patternMap.get("chemistry_structure_prefix"), sentenceContent, inputAS, outputAS, "tmpStructure", sentStartOffset, docLen);

            }
            // Run JAPE transducer to clean up the output
            fireStatusChanged("Processing chemical and drug mentions in " + document.getName());
            try {
                japeTransducer.setDocument(document);
                japeTransducer.setInputASName(inputASName);
                japeTransducer.setOutputASName(outputASName);
                japeTransducer.addProgressListener(this);
                japeTransducer.execute();
            } catch (ExecutionException re) {
                gate.util.Err.println("Unable to run " + japeURL);
                gracefulExit = true;
            } finally {
                japeTransducer.setDocument(null);
            }
            // rename temporary annotations
            renameAnnotations(outputAS, "tmpInorganicMolecule", inorganicChemType);
            renameAnnotations(outputAS, "tmpOrganicMolecule", organicChemType);
            renameAnnotations(outputAS, "tmpDrug", drugType);
            renameAnnotations(outputAS, "tmpFormula", formulaType);

        } else {
            gracefulExit("No sentences to process!");
        }

        fireProcessFinished();
    } // end execute()

    /**
     * Rename annotation
     * @param outputAS          output annotation set
     * @param oldType           old annotation name
     * @param newType           new annotation name
     */
    private void renameAnnotations(AnnotationSet outputAS, String oldType, String newType) {
        AnnotationSet tmpOldAS = outputAS.get(oldType);
        for (Annotation tmpAnn : tmpOldAS) {
            Long startOffset = tmpAnn.getStartNode().getOffset();
            Long endOffset = tmpAnn.getEndNode().getOffset();
            AnnotationSet existingAS = outputAS.getCovering(newType, startOffset, endOffset);
            // If we've already got an annotation of the same name in the same place, don't add a new one
            // just delete the old one
            if (existingAS.isEmpty()) {
                FeatureMap tmpFm = tmpAnn.getFeatures();
                FeatureMap fm = Factory.newFeatureMap();
                fm.putAll(tmpFm);
                try {
                    outputAS.add(startOffset, endOffset, newType, fm);
                    outputAS.remove(tmpAnn);
                } catch (InvalidOffsetException ie) {
                    // shouldn't happen
                }
            } else {
                outputAS.remove(tmpAnn);
            }
        }
    }


    /**
     * Matches possible chemical formula, suppressing false matches (e.g. ClCl, 2Mg etc)
     * @param p             compiled regex Pattern
     * @param content       content string to be matched
     * @param inputAS       input annotation set
     * @param outputAS      output annotation set
     * @param offsetAdjust  offset of previous content string
     * @param max           max progress bar point
     * @throws ExecutionException
     */
    private void doFormulaMatch(Pattern p, String content, AnnotationSet inputAS, AnnotationSet outputAS, Long offsetAdjust, int max) throws ExecutionException {
        if (p == null) { return ; }
        Matcher m = p.matcher(content);
        int i = 0;
        while (m.find()) {
            i++;
            // Progress bar
            fireProgressChanged(i / max);
            if (isInterrupted()) {
                throw new ExecutionException("Execution of MiniChemTagger was interrupted.");
            }
            String term = m.group(0);
            if (term.matches("^[\\(\\[]{0,2}[A-Z].+$") && ! term.matches(".?([A-Z][a-z]{0,2})\\1+.?$")) {
                Long startOffset = new Long(m.start(0));
                Long endOffset = new Long(m.end(0));
                addLookup(inputAS, outputAS, term, "tmpFormula", startOffset + offsetAdjust, endOffset + offsetAdjust);
            }
        }
    }


    /**
     *
     * @param p             compiled regex Pattern
     * @param content       content string to be matched
     * @param inputAS       input annotation set
     * @param outputAS      output annotation set
     * @param outputASType  output annotation type
     * @param offsetAdjust  offset of previous content string
     * @param max           max progress bar point
     * @throws ExecutionException
     */
    private void doMatch(Pattern p, String content, AnnotationSet inputAS, AnnotationSet outputAS, String outputASType, Long offsetAdjust, int max) throws ExecutionException {
        if (p == null) { return ; }
        Matcher m = p.matcher(content);
        int i = 0;
        while (m.find()) {
            i++;
            // Progress bar
            fireProgressChanged(i / max);
            if (isInterrupted()) {
                throw new ExecutionException("Execution of MiniChemTagger was interrupted.");
            }
            String term = m.group(0);
            Long startOffset = new Long(m.start(0));
            Long endOffset = new Long(m.end(0));
            addLookup(inputAS, outputAS, term, outputASType, startOffset + offsetAdjust, endOffset + offsetAdjust);
        }
    }

    /**
     *
     * @param inputAS           input annotation set
     * @param outputAS          output annotation set
     * @param term              String matched
     * @param startOffset       match start offset
     * @param endOffset         match end offset
     */
    private void addLookup(AnnotationSet inputAS, AnnotationSet outputAS, String term, String outputASType, Long startOffset, Long endOffset) {
        try {
            AnnotationSet currAS = inputAS.get(outputASType, startOffset, endOffset);
            if (currAS.isEmpty()) {
                FeatureMap fm = Factory.newFeatureMap();
                fm.put("match", term);
                outputAS.add(startOffset, endOffset, outputASType, fm);
            } else {
                Annotation ann = currAS.iterator().next();
                FeatureMap fm = ann.getFeatures();
                String meta = (String) fm.get("match");
                if (meta != null) {
                    meta = meta + " " + term;
                }
                fm.put("match", meta);
            }
        } catch (InvalidOffsetException ie) {
            // shouldn't happen
            gate.util.Err.println(ie);
        }
    }


    /* Set gracefulExit flag and clean up */
    private void gracefulExit(String msg) {
        gate.util.Err.println(msg);
        cleanup();
        fireProcessFinished();
    }

    @Override
    public void cleanup() {
        Factory.deleteResource(japeTransducer);
    }

    @Override
    public synchronized void interrupt() {
        super.interrupt();
        japeTransducer.interrupt();
    }

    @Override
    public void progressChanged(int i) {
        fireProgressChanged(i);
    }

    @Override
    public void processFinished() {
        fireProcessFinished();
    }

    /* Setters and Getters
     * =======================
     */
    @Optional
    @RunTime
    @CreoleParameter(comment = "Input Annotation Set Name")
    public void setInputASName(String inputASName) {
        this.inputASName = inputASName;
    }

    public String getInputASName() {
        return inputASName;
    }

    @Optional
    @RunTime
    @CreoleParameter(comment = "Output Annotation Set Name")
    public void setOutputASName(String outputASName) {
        this.outputASName = outputASName;
    }

    public String getOutputASName() {
        return outputASName;
    }

    

    @RunTime
    @CreoleParameter(defaultValue = "Drug", comment = "Output Annotation name for drug mentions")
    public void setDrugType(String drugType) {
        this.drugType = drugType;
    }

    public String getDrugType() {
        return drugType;
    }


    @RunTime
    @CreoleParameter(defaultValue = "IUPAC", comment = "Output Annotation name for organic molecule mentions")
    public void setOrganicChemType(String organicChemType) {
        this.organicChemType = organicChemType;
    }

    public String getOrganicChemType() {
        return organicChemType;
    }


    @RunTime
    @CreoleParameter(defaultValue = "IUPAC", comment = "Output Annotation name for inorganic molecule mentions")
    public void setInorganicChemType(String inorganicChemType) {
        this.inorganicChemType = inorganicChemType;
    }

    public String getInorganicChemType() {
        return inorganicChemType;
    }


    @RunTime
    @CreoleParameter(defaultValue = "ChemFormula", comment = "Output Annotation name for chemical formula mentions")
    public void setFormulaType(String formulaType) {
        this.formulaType = formulaType;
    }

    public String getFormulaType() {
        return formulaType;
    }


	@CreoleParameter(defaultValue = "2", comment = "Minimum length of prefix string before a suffix")
    public void setMinPrefixLength(Integer minPrefixLength) {
        this.minPrefixLength = minPrefixLength;
    }

    public Integer getMinPrefixLength() {
        return minPrefixLength;
    }
    
    
    public URL getConfigFileURL() {
        return configFileURL;
    }

    @CreoleParameter(defaultValue = "resources/config.txt",
    comment = "Location of configuration file")
    public void setConfigFileURL(URL configFileURL) {
        this.configFileURL = configFileURL;
    }

    @Optional
    @RunTime
    @CreoleParameter(defaultValue = ANNIEConstants.SENTENCE_ANNOTATION_TYPE,
    comment = "Sentence annotation name")
    public void setSentenceType(String sentenceName) {
        this.sentenceType = sentenceName;
    }

    public String getSentenceType() {
        return sentenceType;
    }

 
    @CreoleParameter(defaultValue = "resources/jape/main.jape",
    comment = "Location of main JAPE file")
    public void setJapeURL(URL japeURL) {
        this.japeURL = japeURL;
    }

    public URL getJapeURL() {
        return japeURL;
    }
}
