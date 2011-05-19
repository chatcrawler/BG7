/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.era7.bioinfo.annotation.embl;

import com.era7.lib.bioinfo.bioinfoutil.Executable;
import com.era7.lib.bioinfo.bioinfoutil.model.Feature;
import com.era7.lib.bioinfoxml.Annotation;
import com.era7.lib.bioinfoxml.ContigXML;
import com.era7.lib.bioinfoxml.PredictedGene;
import com.era7.lib.bioinfoxml.PredictedGenes;
import com.era7.lib.bioinfoxml.PredictedRna;
import com.era7.lib.bioinfoxml.PredictedRnas;
import com.era7.lib.bioinfoxml.embl.EmblXML;
import com.era7.lib.era7xmlapi.model.XMLElementException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jdom.Element;

/**
 * 
 * @author Pablo Pareja Tobes <ppareja@era7.com>
 */
public class ExportEmblFiles implements Executable {
    
    public static final int DEFAULT_INDENTATION_NUMBER_OF_WHITESPACES = 3;
    public static final int LINE_MAX_LENGTH = 80;

    public void execute(ArrayList<String> array) {
        String[] args = new String[array.size()];
        for (int i = 0; i < array.size(); i++) {
            args[i] = array.get(i);
        }
        main(args);
    }

    public static void main(String[] args) {

        if (args.length != 4) {
            System.out.println("This program expects 4 parameters: \n"
                    + "1. Gene annotation XML result filename \n"
                    + "2. Embl general info XML filename\n"
                    + "3. FNA file with both header and contig sequence\n"
                    + "4. Prefix string for output files\n");
        } else {


            String annotationFileString = args[0];
            String emblXmlFileString = args[1];
            String fnaContigFileString = args[2];
            String outFileString = args[3];

            File annotationFile = new File(annotationFileString);
            File fnaContigFile = new File(fnaContigFileString);
            File emblXmlFile = new File(emblXmlFileString);
            

            try {


                //-----READING XML FILE WITH ANNOTATION DATA------------
                BufferedReader reader = new BufferedReader(new FileReader(annotationFile));
                String tempSt;
                StringBuilder stBuilder = new StringBuilder();
                while ((tempSt = reader.readLine()) != null) {
                    stBuilder.append(tempSt);
                }
                //Closing file
                reader.close();

                Annotation annotation = new Annotation(stBuilder.toString());
                //-------------------------------------------------------------
                
                //-----READING XML GENERAL INFO FILE------------
                 reader = new BufferedReader(new FileReader(emblXmlFile));
                stBuilder = new StringBuilder();
                while ((tempSt = reader.readLine()) != null) {
                    stBuilder.append(tempSt);
                }
                //Closing file
                reader.close();

                EmblXML emblXML = new EmblXML(stBuilder.toString());
                //-------------------------------------------------------------

                //-------------PARSING CONTIGS & THEIR SEQUENCES------------------
                HashMap<String, String> contigsMap = new HashMap<String, String>();
                reader = new BufferedReader(new FileReader(fnaContigFile));
                stBuilder.delete(0, stBuilder.length());
                String currentContigId = "";

                while ((tempSt = reader.readLine()) != null) {
                    if (tempSt.charAt(0) == '>') {
                        if (stBuilder.length() > 0) {
                            contigsMap.put(currentContigId, stBuilder.toString());
                            stBuilder.delete(0, stBuilder.length());
                        }
                        currentContigId = tempSt.substring(1).trim().split(" ")[0].split("\t")[0];
                        System.out.println("currentContigId = " + currentContigId);
                    } else {
                        stBuilder.append(tempSt);
                    }
                }
                if (stBuilder.length() > 0) {
                    contigsMap.put(currentContigId, stBuilder.toString());
                }
                reader.close();
                //-------------------------------------------------------------

                List<Element> contigList = annotation.asJDomElement().getChild(PredictedGenes.TAG_NAME).getChildren(ContigXML.TAG_NAME);
                List<Element> contigListRna = annotation.asJDomElement().getChild(PredictedRnas.TAG_NAME).getChildren(ContigXML.TAG_NAME);
                HashMap<String, ContigXML> contigsRnaMap = new HashMap<String, ContigXML>();
                for (Element element : contigListRna) {
                    ContigXML rnaContig = new ContigXML(element);
                    contigsRnaMap.put(rnaContig.getId(), rnaContig);
                }
                
                //-----------CONTIGS LOOP-----------------------

                for (Element elem : contigList) {

                    ContigXML currentContig = new ContigXML(elem);

                    String mainSequence = contigsMap.get(currentContig.getId());
                    //removing the sequence from the map so that afterwards contigs
                    //with no annotations can be identified
                    contigsMap.remove(currentContig.getId());

                    exportContigToEmbl(currentContig, emblXML, outFileString, mainSequence,contigsRnaMap);

                }

                System.out.println("There are " + contigsMap.size() + " contigs with no annotations...");

                System.out.println("generating their embl files...");
                Set<String> keys = contigsMap.keySet();
                for (String tempKey : keys) {
                    System.out.println("generating file for contig: " + tempKey);
                    ContigXML currentContig = new ContigXML();
                    currentContig.setId(tempKey);
                    String mainSequence = contigsMap.get(currentContig.getId());
                    exportContigToEmbl(currentContig, emblXML, outFileString, mainSequence,contigsRnaMap);

                }


                System.out.println("Embl files succesfully created! :)");

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    private static void exportContigToEmbl(ContigXML currentContig,
            EmblXML emblXml,
            String outFileString,
            String mainSequence,
            HashMap<String, ContigXML> contigsRnaMap) throws IOException, XMLElementException {

        File outFile = new File(outFileString + currentContig.getId() + ".embl");
        BufferedWriter outBuff = new BufferedWriter(new FileWriter(outFile));

        //-------------------------ID line-----------------------------------
        String idLineSt = "";
        idLineSt += "ID" + getWhiteSpaces(DEFAULT_INDENTATION_NUMBER_OF_WHITESPACES);
        idLineSt += currentContig.getId() + "; " + currentContig.getId() + "; ";
        idLineSt += emblXml.getId() + "\n";        
        outBuff.write(idLineSt);
        
        outBuff.write("XX" + "\n");
        
        outBuff.write("DE" + getWhiteSpaces(DEFAULT_INDENTATION_NUMBER_OF_WHITESPACES) +
                emblXml.getDefinition() + " " + currentContig.getId() + "\n");
        
        outBuff.write("XX" + "\n");
        
        outBuff.write("AC" + getWhiteSpaces(DEFAULT_INDENTATION_NUMBER_OF_WHITESPACES) +
                ";" + "\n");
        
        outBuff.write("XX" + "\n");
        
        outBuff.write("KW" + getWhiteSpaces(DEFAULT_INDENTATION_NUMBER_OF_WHITESPACES) +
                "." + "\n");
        
        outBuff.write("XX" + "\n");
        
        outBuff.write("OS" + getWhiteSpaces(DEFAULT_INDENTATION_NUMBER_OF_WHITESPACES) +
                emblXml.getOrganism() + "\n");
        
        String[] lineageSplit = emblXml.getOrganismCompleteTaxonomyLineage().split(";");
        String tempLineageLine = "OC" + getWhiteSpaces(DEFAULT_INDENTATION_NUMBER_OF_WHITESPACES); 
        
        for (String lineageSt : lineageSplit) {
            if((tempLineageLine.length() + lineageSt.length() + 1) < LINE_MAX_LENGTH){
                tempLineageLine += lineageSt + ";";                
            }else{
                outBuff.write(tempLineageLine + "\n");
                if(lineageSt.charAt(0) == ' '){
                    lineageSt = lineageSt.substring(1);
                }
                tempLineageLine = "OC" + getWhiteSpaces(DEFAULT_INDENTATION_NUMBER_OF_WHITESPACES) + lineageSt + ";";
            }            
        }
        if(tempLineageLine.length() > 0){
            outBuff.write(tempLineageLine + "\n");
        }

        outBuff.write("XX" + "\n");
        
        outBuff.write("FH   Key             Location/Qualifiers" + "\n");
        
        String sourceSt = "FT   source          ";
        sourceSt += "1.." + mainSequence.length() + "\n";
        outBuff.write(sourceSt);

        outBuff.write("FT                   /organism=\"" + emblXml.getOrganism() + "\"" + "\n");
        outBuff.write("FT                   /mol_type=\"" + emblXml.getMolType() + "\"" + "\n");
        outBuff.write("FT                   /strain=\"" + emblXml.getStrain() + "\"" + "\n");
        

        //------Hashmap with key = gene/rna id and the value =
        //---- respective String exactly as is must be written to the result file---------------------------
        HashMap<String, String> genesRnasMixedUpMap = new HashMap<String, String>();

        TreeSet<Feature> featuresTreeSet = new TreeSet<Feature>();

        //----------------------GENES LOOP----------------------------
        List<Element> genesList = currentContig.asJDomElement().getChildren(PredictedGene.TAG_NAME);
        for (Element element : genesList) {

            PredictedGene gene = new PredictedGene(element);
            Feature tempFeature = new Feature();
            tempFeature.setId(gene.getId());
            if (gene.getStrand().equals(PredictedGene.POSITIVE_STRAND)) {
                tempFeature.setBegin(gene.getStartPosition());
                tempFeature.setEnd(gene.getEndPosition());
            } else {
                tempFeature.setBegin(gene.getEndPosition());
                tempFeature.setEnd(gene.getStartPosition());
            }
            featuresTreeSet.add(tempFeature);
            genesRnasMixedUpMap.put(gene.getId(), getGeneStringForEmbl(gene));

        }
        //--------------------------------------------------------------

        //Now rnas are added (if there are any) so that everything can be sort afterwards
        ContigXML contig = contigsRnaMap.get(currentContig.getId());
        if (contig != null) {
            List<Element> rnas = contig.asJDomElement().getChildren(PredictedRna.TAG_NAME);
            for (Element tempElem : rnas) {
                PredictedRna rna = new PredictedRna(tempElem);
                Feature tempFeature = new Feature();
                tempFeature.setId(rna.getId());
                if (rna.getStrand().equals(PredictedGene.POSITIVE_STRAND)) {
                    tempFeature.setBegin(rna.getStartPosition());
                    tempFeature.setEnd(rna.getEndPosition());
                } else {
                    tempFeature.setBegin(rna.getEndPosition());
                    tempFeature.setEnd(rna.getStartPosition());
                }
                featuresTreeSet.add(tempFeature);
                genesRnasMixedUpMap.put(rna.getId(), getRnaStringForEmbl(rna));
            }
        }

        //Once genes & rnas are sorted, we just have to write them
        for (Feature f : featuresTreeSet) {
            outBuff.write(genesRnasMixedUpMap.get(f.getId()));
        }



        //--------------ORIGIN-----------------------------------------
        outBuff.write("SQ   Sequence " + mainSequence.length() + " BP;" + "\n");
        int maxDigits = 10;
        int positionCounter = 1;
        int maxBasesPerLine = 60;
        int currentBase = 0;
        int seqFragmentLength = 10;

//                    System.out.println("currentContig.getId() = " + currentContig.getId());
//                    System.out.println("mainSequence.length() = " + mainSequence.length());
//                    System.out.println(contigsMap.get(currentContig.getId()).length());

        for (currentBase = 0; (currentBase + maxBasesPerLine) < mainSequence.length(); positionCounter += maxBasesPerLine) {
            
            String tempLine = getWhiteSpaces(5);
            for (int i = 1; i <= (maxBasesPerLine / seqFragmentLength); i++) {
                tempLine += " " + mainSequence.substring(currentBase, currentBase + seqFragmentLength);
                currentBase += seqFragmentLength;
            }
            String posSt = String.valueOf(positionCounter - 1 + maxBasesPerLine);
            tempLine += getWhiteSpaces(maxDigits - posSt.length()) + posSt;
            outBuff.write(tempLine + "\n");
        }

        if (currentBase < mainSequence.length()) {
                        
            String lastLine = getWhiteSpaces(5);
            while (currentBase < mainSequence.length()) {
                if ((currentBase + seqFragmentLength) < mainSequence.length()) {
                    lastLine += " " + mainSequence.substring(currentBase, currentBase + seqFragmentLength);
                } else {
                    lastLine += " " + mainSequence.substring(currentBase, mainSequence.length());
                }
                
                currentBase += seqFragmentLength;
            }
            String posSt = String.valueOf(mainSequence.length());
            lastLine += getWhiteSpaces(LINE_MAX_LENGTH - posSt.length() - lastLine.length() + 1) + posSt;
            
            outBuff.write(lastLine + "\n");
        }

        //--------------------------------------------------------------


        //--- finally I have to add the string "//" in the last line--
        outBuff.write("//\n");

        outBuff.close();

    }

    private static String getWhiteSpaces(int number) {
        String result = "";
        for (int i = 0; i < number; i++) {
            result += " ";
        }
        return result;
    }

    private static String patatizaEnLineas(String header,
            String value,
            int numberOfWhiteSpacesForIndentation,
            boolean putQuotationMarksInTheEnd) {

        //value = value.toUpperCase();
        String result = "";

        result += header;

        int lengthWithoutIndentation = LINE_MAX_LENGTH - numberOfWhiteSpacesForIndentation - 2;

        if (value.length() < (LINE_MAX_LENGTH - header.length())) {
            result += value;
            if (putQuotationMarksInTheEnd) {
                result += "\"";
            }
            result += "\n";
        } else if (value.length() == (LINE_MAX_LENGTH - header.length())) {
            result += value + "\n";
            if (putQuotationMarksInTheEnd) {
                result += getWhiteSpaces(numberOfWhiteSpacesForIndentation) + "\"\n";
            }
        } else {
            result += value.substring(0, (LINE_MAX_LENGTH - header.length())) + "\n";
            value = value.substring((LINE_MAX_LENGTH - header.length()), value.length());

            while (value.length() > lengthWithoutIndentation) {
                result += "FT" + getWhiteSpaces(numberOfWhiteSpacesForIndentation)
                        + value.substring(0, lengthWithoutIndentation) + "\n";
                value = value.substring(lengthWithoutIndentation, value.length());
            }
            if (value.length() == lengthWithoutIndentation) {
                result += "FT" + getWhiteSpaces(numberOfWhiteSpacesForIndentation)
                        + value + "\n";
                if (putQuotationMarksInTheEnd) {
                    result += getWhiteSpaces(numberOfWhiteSpacesForIndentation) + "\"\n";
                }
            } else {
                result += "FT" + getWhiteSpaces(numberOfWhiteSpacesForIndentation)
                        + value;
                if (putQuotationMarksInTheEnd) {
                    result += "\"";
                }
                result += "\n";
            }
        }
        return result;
    }
    

    private static String getGeneStringForEmbl(PredictedGene gene) {

        StringBuilder geneStBuilder = new StringBuilder();
        boolean negativeStrand = gene.getStrand().equals(PredictedGene.NEGATIVE_STRAND);

        String positionsString = "";


        if (negativeStrand) {

            positionsString += "complement(";
            if (!gene.getEndIsCanonical()) {
                positionsString += "<";
            }
            positionsString += gene.getEndPosition() + "..";

            if (!gene.getStartIsCanonical()) {
                positionsString += ">";
            }
            positionsString += gene.getStartPosition() + ")";

        } else {

            if (!gene.getStartIsCanonical()) {
                positionsString += "<";
            }
            positionsString += gene.getStartPosition() + "..";
            if (!gene.getEndIsCanonical()) {
                positionsString += ">";
            }

            positionsString += gene.getEndPosition();

        }

        //gene part
        String tempGeneStr = "FT   "
                + "gene"
                + getWhiteSpaces(12)
                + positionsString + "\n";

        geneStBuilder.append(tempGeneStr);
        geneStBuilder.append(patatizaEnLineas("FT" + 
                getWhiteSpaces(19) + "/product=\"",
                gene.getProteinNames(),
                19,
                true));

        String tempCDSString = "FT" + getWhiteSpaces(DEFAULT_INDENTATION_NUMBER_OF_WHITESPACES)
                + "CDS"
                + getWhiteSpaces(13);

        tempCDSString += positionsString + "\n";
        geneStBuilder.append(tempCDSString);

        geneStBuilder.append(patatizaEnLineas("FT" + 
                getWhiteSpaces(19) + "/product=\"",
                gene.getProteinNames(),
                19,
                true));

        if (gene.getProteinSequence() != null) {
            if (!gene.getProteinSequence().equals("")) {
                geneStBuilder.append(patatizaEnLineas( "FT" +
                        getWhiteSpaces(19) + "/translation=\"",
                        gene.getProteinSequence(),
                        19,
                        true));
            }
        }

        return geneStBuilder.toString();

    }

    private static String getRnaStringForEmbl(PredictedRna rna) {

        StringBuilder rnaStBuilder = new StringBuilder();
        boolean negativeStrand = rna.getStrand().equals(PredictedRna.NEGATIVE_STRAND);

        String positionsString = "";


        if (negativeStrand) {

            positionsString += "complement(";
//            if (!rna.getEndIsCanonical()) {
//                positionsString += "<";
//            }
            positionsString += "<" + rna.getEndPosition() + ".." + ">" + rna.getStartPosition();
//            if (!rna.getStartIsCanonical()) {
//                positionsString += ">";
//            }
            positionsString += ")";

        } else {

//            if (!rna.getStartIsCanonical()) {
//                positionsString += "<";
//            }
            positionsString += "<" + rna.getStartPosition() + ".." + ">" + rna.getEndPosition();
//            if (!rna.getEndIsCanonical()) {
//                positionsString += ">";
//            }

        }

        //gene part
        String tempRnaStr = "FT   "
                + "gene"
                + getWhiteSpaces(12)
                + positionsString + "\n";

        rnaStBuilder.append(tempRnaStr);
        rnaStBuilder.append(patatizaEnLineas( "FT" + 
                getWhiteSpaces(19) + "/product=\"",
                rna.getAnnotationUniprotId().split("\\|")[3],
                19,
                true));

        String tempRNAString = "FT   "
                + "rna"
                + getWhiteSpaces(13);

        tempRNAString += positionsString + "\n";
        rnaStBuilder.append(tempRNAString);

        rnaStBuilder.append(patatizaEnLineas( "FT" + 
                getWhiteSpaces(19) + "/product=\"",
                rna.getAnnotationUniprotId().split("\\|")[3],
                19,
                true));

        return rnaStBuilder.toString();

    }
}