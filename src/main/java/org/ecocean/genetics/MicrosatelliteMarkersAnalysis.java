package org.ecocean.genetics;

import java.util.ArrayList;
import java.util.List;

public class MicrosatelliteMarkersAnalysis extends GeneticAnalysis{


  private static final long serialVersionUID = 2164590533347366885L;
  private static String type="MicrosatelliteMarkers";
  private List<Locus> loci;
  
  //Empty constructor required for JDO.
  //DO NOT USE
  public MicrosatelliteMarkersAnalysis() {}
  
  public MicrosatelliteMarkersAnalysis(String analysisID, String sampleID, String correspondingEncounterNumber) {
    super(analysisID, type, correspondingEncounterNumber, sampleID);
    loci=new ArrayList<Locus>();
  }
  
  public MicrosatelliteMarkersAnalysis(String analysisID, String sampleID, String correspondingEncounterNumber, ArrayList<Locus> loci) {
    super(analysisID, type, correspondingEncounterNumber, sampleID);
    this.loci=loci;
  }
  
  public List<Locus> getLoci(){return loci;}
  public void addLocus(Locus l){
    if(!loci.contains(l)){loci.add(l);}
  }
  public void removeLocus(Locus l){
    if(loci.contains(l)){loci.remove(l);}
  }
  public void removeLocus(int i){
    if(loci.size()>i){loci.remove(i);}
  }
  public void setLoci(ArrayList<Locus> loci){this.loci=loci;}

}