/* PublicDBDocumentEntry.java
 *
 * created: Sat Sep 11 1999
 *
 * This file is part of Artemis
 *
 * Copyright (C) 1999  Genome Research Limited
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * $Header: //tmp/pathsoft/artemis/uk/ac/sanger/artemis/io/PublicDBDocumentEntry.java,v 1.14 2008-07-04 15:20:09 tjc Exp $
 */

package uk.ac.sanger.artemis.io;

import uk.ac.sanger.artemis.components.genebuilder.GeneUtils;
import uk.ac.sanger.artemis.util.*;

import java.io.IOException;

/**
 *  This class extends the Entry class with the data for the entry coming from
 *  a Document object.  The Document must contain an EMBL entry or a GENBANK
 *  entry.
 *
 *  @author Kim Rutherford
 *  @version $Id: PublicDBDocumentEntry.java,v 1.14 2008-07-04 15:20:09 tjc Exp $
 **/

public class PublicDBDocumentEntry extends SimpleDocumentEntry
    implements DocumentEntry 
{
  
  final private static String[][] MAP_KEYS = 
  {
    {"pseudogenic_transcript", "mRNA"},
    {"pseudogenic_exon", "CDS"},
    {"pseudogene", "gene"},
    {DatabaseDocument.EXONMODEL, "CDS"},
    {"polypeptide_motif", "CDS_motif"},
    {"five_prime_UTR", "5'UTR"},
    {"three_prime_UTR", "3'UTR"}
  };
  
  /**
   *  Create a new PublicDBDocumentEntry object associated with the given
   *  Document.
   *  @param entry_information The EntryInformation object of the new Entry.
   *  @param document This is the file that we will read from.  This is also
   *    used for saving the entry back to the file it came from and to give
   *    the new object a name.
   *  @param listener The object that will listen for ReadEvents.
   *  @exception IOException thrown if there is a problem reading the entry -
   *    most likely ReadFormatException.
   *  @exception EntryInformationException Thrown if force is false and if this
   *    Entry cannot contain the Key, Qualifier or Key/Qualifier combination of
   *    one of the features in the given Entry.
   **/
  PublicDBDocumentEntry(final EntryInformation entry_information,
                        final Document document, final ReadListener listener)
      throws IOException, EntryInformationException 
  {
    super(entry_information, document, listener);
  }

  /**
   *  Create a new PublicDBDocumentEntry that will be a copy of the given
   *  Entry and has no Document associated with it.  The new
   *  PublicDBDocumentEntry cannot be saved to a file with save () unless save
   *  (Document) has been called first.
   *  @param entry_information The EntryInformation object of the new Entry.
   *  @param force If true then invalid qualifiers and any features with
   *    invalid keys in the new Entry will be quietly thrown away.  "Invalid"
   *    means that the key/qualifier is not allowed to occur in an Entry of
   *    this type (probably determined by the EntryInformation object of this
   *    Entry).  If false an EntryInformationException will be thrown for
   *    invalid keys or qualifiers.
   *  @exception EntryInformationException Thrown if force is false and if this
   *    Entry cannot contain the Key, Qualifier or Key/Qualifier combination of
   *    one of the features in the given Entry.
   **/
  public PublicDBDocumentEntry(final EntryInformation entry_information,
                               final Entry new_entry, final boolean force)
      throws EntryInformationException 
  {
    super(entry_information, new_entry, force);
  }

  /**
   *  Create a new empty PublicDBDocumentEntry object that has no Document
   *  associated with it.  The new PublicDBDocumentEntry cannot be saved to a
   *  file with save () unless save (Document) has been called first.  The
   *  save (Document) method will assign a Document.
   *  @param entry_information The EntryInformation object of the Entry that
   *    will contain this Feature.
   **/
  public PublicDBDocumentEntry(final EntryInformation entry_information) 
  {
    super(entry_information);
  }

  /**
   *  If the given feature can be added directly to this Entry, then return
   *  it, otherwise create and return a new feature of the appropriate type.
   *  @param copy if true then always new a new copy of the Feature.
   **/
  protected Object makeNativeFeature(final Feature feature,
                                     final boolean copy) 
  {
    if (!copy && (feature instanceof EmblStreamFeature &&
                  this instanceof EmblDocumentEntry ||
                  feature instanceof GenbankStreamFeature &&
                  this instanceof GenbankDocumentEntry)) 
    {
      return (PublicDBStreamFeature) feature;
    } 
    else 
    {
      if(feature instanceof GFFStreamFeature && feature.getEntry() instanceof DatabaseDocumentEntry)
        return mapGffToNativeFeature(feature);
      else if(feature instanceof GFFStreamFeature)
        return new GFFStreamFeature(feature);
      else if (this instanceof EmblDocumentEntry)
        return new EmblStreamFeature(feature);
      else
        return new GenbankStreamFeature(feature);
    }
  }

  /**
   * Map GFF features to EMBL/Genbank
   * @param feature
   * @return
   */
  private Object mapGffToNativeFeature(final Feature feature)
  {
    final String[] QUALIFIERS_TO_REMOVE = 
        { 
          "timelastmodified", 
          "ID", 
          "comment",   // convert to note
          "feature_id", 
          "Parent", 
          "Derives_from",
          "feature_relationship_rank",
          "isObsolete"
        };
    
    Key key = feature.getKey();
    QualifierVector qualifiers = feature.getQualifiers().copy();
    
    if(getEntryInformation().isValidQualifier(QUALIFIERS_TO_REMOVE[0]))
    {
      if(key.getKeyString().startsWith("pseudo"))
        key = handlePseudo(key,qualifiers);
      key = mapKeys(key);
      
      try
      {
        if(this instanceof EmblDocumentEntry)
          return new EmblStreamFeature (
              key, 
              feature.getLocation(), 
              qualifiers);
        else
          return new GenbankStreamFeature (
            key, 
            feature.getLocation(), 
            qualifiers);
      }
      catch(InvalidRelationException e)
      {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    
    
    // flatten gene model - combining qualifiers
    if(key.getKeyString().equals(DatabaseDocument.EXONMODEL))
    {
      ChadoCanonicalGene chadoGene = ((GFFStreamFeature)feature).getChadoGene();

      final String name = GeneUtils.getUniqueName(feature);
      final String transcriptName = chadoGene.getTranscriptFromName(name);
      
      // add protein qualifiers to CDS
      final Feature protein = chadoGene.getProteinOfTranscript(transcriptName);
      if(protein != null)
        qualifiers.addAll(protein.getQualifiers().copy());
      
      // add gene qualifiers to CDS
      qualifiers.addAll(chadoGene.getGene().getQualifiers().copy());
    }
    
    final String[][] QUALIFIERS_TO_MAP =
    {
        {"comment", "note"},
        {"Dbxref", "db_xref"},
        {"private", "note"},
        {"orthologous_to", "ortholog"},
        {"paralogous_to", "paralog"}
    };
    
    try
    {
      for(int i=0; i<QUALIFIERS_TO_MAP.length; i++)
      {
        if(!getEntryInformation().isValidQualifier(QUALIFIERS_TO_MAP[i][0]))
        {
          changeQualifierName(qualifiers, QUALIFIERS_TO_MAP[i][0], QUALIFIERS_TO_MAP[i][1]);
        }
      }
      
      for(int i=0; i<QUALIFIERS_TO_REMOVE.length; i++)
      {
        if(!getEntryInformation().isValidQualifier(QUALIFIERS_TO_REMOVE[i]))
        {
          qualifiers.removeQualifierByName(QUALIFIERS_TO_REMOVE[i]);
          qualifiers.removeQualifierByName(QUALIFIERS_TO_REMOVE[i]);
          qualifiers.removeQualifierByName(QUALIFIERS_TO_REMOVE[i]);
        }
      }
      
      if(key.getKeyString().startsWith("pseudo"))
        key = handlePseudo(key, qualifiers);
     
      key = mapKeys(key);
      
      if(key.getKeyString().equals("polypeptide"))
        return null;
      else if(key.getKeyString().equals("gene"))
        return null;
      else if(key.getKeyString().equals("centromere"))
        return null;
      else if(key.getKeyString().equals("transcript") || 
              key.getKeyString().equals("mRNA"))
        return null;
      
      
      
      //
      // create separate exon features for each range in a CDS
      // (returns an array of SimpleDocumentFeature)
      /*
      if(key.getKeyString().equals("CDS"))
      {
        RangeVector ranges = feature.getLocation().getRanges();
        SimpleDocumentFeature[] features = 
          new SimpleDocumentFeature[ranges.size() + 1];
        features[0] = new EmblStreamFeature(key, feature.getLocation(),
            qualifiers);

        for(int i = 0; i < ranges.size(); i++)
        {
          try
          {
            features[i + 1] = new EmblStreamFeature(new Key("exon"),
                new Location((Range) ranges.get(i)), qualifiers);
          }
          catch(OutOfRangeException e)
          {
            e.printStackTrace();
          }
        }
        return features;
      }
      */
      
      
      if(this instanceof EmblDocumentEntry)
        return new EmblStreamFeature (
            key, 
            feature.getLocation(), 
            qualifiers);
      else
        return new GenbankStreamFeature (
            key, 
            feature.getLocation(), 
            qualifiers);
    }
    catch(InvalidRelationException e)
    {
      e.printStackTrace();
      if(feature instanceof DatabaseStreamFeature)
        return new EmblStreamFeature ();
      else
        return new GenbankStreamFeature ();
    }
  }
  
  /**
   * 
   * @param key
   * @param qualifiers
   */
  private Key handlePseudo(Key key, final QualifierVector qualifiers)
  {
    if(key.getKeyString().equals("pseudogenic_transcript"))
      key = new Key("mRNA");
    else if(key.getKeyString().equals("pseudogenic_exon"))
      key = new Key("CDS");
    else if(key.getKeyString().equals("pseudogene"))
      key = new Key("gene");

    qualifiers.setQualifier(new Qualifier("pseudo"));

    if(!getEntryInformation().isValidQualifier(key, "pseudo"))
    {
      try
      {
        getEntryInformation().addQualifierInfo(
            new QualifierInfo("pseudo", QualifierInfo.NO_VALUE, null, null,
                true));
      }
      catch(QualifierInfoException e){}
    }
    return key;
  }
  
  /**
   * 
   * @param key
   * @return
   */
  protected static Key mapKeys(Key key)
  {
    for(int i=0; i<MAP_KEYS.length; i++)
    {
      if(key.getKeyString().equals(MAP_KEYS[i][0]))
        return new Key(MAP_KEYS[i][1]);
    }
    return key;
  }
  
  /**
   * Change the name of a qualifier
   * 
   * @param qualifiers
   * @param oldName
   * @param newName
   */
  private void changeQualifierName(final QualifierVector qualifiers, 
                                   final String oldName, 
                                   final String newName)
  {
    int index = qualifiers.indexOfQualifierWithName(oldName);
    
    if(index > -1)
    {
      StringVector values = ((Qualifier)qualifiers.get(index)).getValues();
      qualifiers.removeQualifierByName(oldName);
      Qualifier newQualifier = new Qualifier(newName, values);
      qualifiers.setQualifier(newQualifier);
    }  
  }
  
  /**
   *  If the given Sequence can be added directly to this Entry, then return a
   *  copy of it, otherwise create and return a new feature of the appropriate
   *  type for this Entry.
   **/
  protected StreamSequence makeNativeSequence (final Sequence sequence) 
  {
    if(this instanceof EmblDocumentEntry)
      return new EmblStreamSequence (sequence);
    else 
      return new GenbankStreamSequence (sequence);
  }
}
