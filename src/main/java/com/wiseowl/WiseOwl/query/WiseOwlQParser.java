/* Copyright 2008-2011 Grant Ingersoll, Thomas Morton and Drew Farris
        *
        *    Licensed under the Apache License, Version 2.0 (the "License");
        *    you may not use this file except in compliance with the License.
        *    You may obtain a copy of the License at
        *
        *        http://www.apache.org/licenses/LICENSE-2.0
        *
        *    Unless required by applicable law or agreed to in writing, software
        *    distributed under the License is distributed on an "AS IS" BASIS,
        *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        *    See the License for the specific language governing permissions and
        *    limitations under the License.
        * -------------------
        * To purchase or learn more about Taming Text, by Grant Ingersoll, Thomas Morton and Drew Farris, visit
        * http://www.manning.com/ingersoll
        * This code has been modified and upgraded by WiseOwl Team, Avtar Singh, Sumit Kumar and Yuvraj Singh.
        * Modifications are copyright 2016-2017 WiseOwl Team, Avtar Singh, Sumit Kumar and Yuvraj Singh
        * https://www.linkedin.com/in/avtar-singh-6a481a124/
       */

package com.wiseowl.WiseOwl.query;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.spans.SpanBoostQuery;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.QParser;
import org.apache.solr.search.SyntaxError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * The QuestionQParser takes in a natural language question and produces a Lucene {@link org.apache.lucene.search.spans.SpanNearQuery}
 *
 */
public class WiseOwlQParser extends QParser implements OWLParams  {
  private transient static Logger log = LoggerFactory.getLogger(WiseOwlQParser.class);
  private Parser parser;
  private AnswerTypeClassifier atc;
  private Map<String,String> atm;

  public WiseOwlQParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req,
                         Parser parser, AnswerTypeClassifier atc,
                         Map<String,String> answerTypeMap) {
    super(qstr, localParams, params, req);
    this.parser = parser;
    this.atc = atc;
    this.atm = answerTypeMap;
  }

  @Override
  public Query parse() throws SyntaxError {

    //<start id="qqp.parse"/>
    Parse parse = ParserTool.parseLine(qstr, parser, 1)[0];//<co id="qqp.parseLine"/>
    /*
    <calloutlist>
        <callout arearefs="qqp.parseLine"><para>Parse the question using the <classname>TreebankParser</classname>.  The resulting <classname>Parse</classname> object can then be utilized by the classifier to determine the Answer Type.</para></callout>
    </calloutlist>
    */
    //<end id="qqp.parse"/>
    //<start id="qqp.answerType"/>
   // String type = "P";
    String type= atc.computeAnswerType(parse);
    String mt = atm.get(type);
   if(mt.equals("DESCRIPTION"))
    {
    	BooleanQuery bq;
    	BooleanQuery.Builder builder= new BooleanQuery.Builder();
    	//BooleanQuery bq=new BooleanQuery(false, 0);
    	String field="text";
    	SchemaField sf = req.getSchema().getFieldOrNull(field);
        try {
          Analyzer analyzer = sf.getType().getQueryAnalyzer();
          TokenStream ts = analyzer.tokenStream(field,
                  new StringReader(qstr));
          ts.reset();
          CharTermAttribute tok=null;
          while (ts.incrementToken()) {//<co id="qqp.addTerms"/>
        	  tok=ts.getAttribute(CharTermAttribute.class);    	  
        	  String term = tok.toString();
            //ts.reset();
        	  //log.warn("terms {} ",term);
            builder.add(new TermQuery(new Term(field, term)), BooleanClause.Occur.SHOULD);
          }
          ts.close();
        } catch (IOException e) {
          throw new SyntaxError(e.getLocalizedMessage());
        }
        bq=builder.build();
        return bq;
    	//return new TermQuery(new Term("title", "she"));

    }else
    {
    //<end id="qqp.answerType"/>
    String field = "text";
    		//params.get(QUERY_FIELD);
    	//String field="text";
    SchemaField sp = req.getSchema().getFieldOrNull(field);
    if (sp == null) {
      throw new SolrException(ErrorCode.SERVER_ERROR,"Undefined field: "+field);
    }
    //<start id="qqp.query"/>
    List<SpanQuery> sql = new ArrayList<SpanQuery>();
    if (mt != null) {//<co id="qqp.handleAT"/>
      String[] parts = mt.split("\\|");
      if (parts.length == 1) {
        sql.add(new SpanTermQuery(new Term(field, mt.toLowerCase())));
      } else {
        for (int pi = 0; pi < parts.length; pi++) {
          sql.add(new SpanTermQuery(new Term(field, parts[pi].toLowerCase())));
        }
      }
    }
    log.warn("answer type mt : {} {} ",mt,type);
    FocusNoun fn=new FocusNoun();
    String fnn[]=null;
	try {
		fnn=fn.getFocusNoun(qstr);
	} catch (IOException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	}
    try {
      Analyzer analyzer = sp.getType().getQueryAnalyzer();
      TokenStream ts = analyzer.tokenStream(field,
              new StringReader(qstr));
      ts.reset();
      CharTermAttribute tok=null;

      while (ts.incrementToken()) {//<co id="qqp.addTerms"/>
    	  tok=ts.getAttribute(CharTermAttribute.class);    	  
    	  String term = tok.toString();
    	  log.warn("terms boosted {} ",term );
    	  if(fnn!=null)
    	  if(term.equals(fnn[0]) || term.equals(fnn[1]))
    	  {
    		  SpanQuery sq= new SpanTermQuery(new Term(field, term));
        	  sql.add(new SpanBoostQuery(sq,100f));
    	  }else
    	  {
    		  SpanQuery sq= new SpanTermQuery(new Term(field, term));
        	  sql.add(new SpanBoostQuery(sq,5f));
    	  }
    	 
       // sql.add(new SpanTermQuery(new Term(field, term)));
      }
      ts.close();
    } catch (IOException e) {
      throw new SyntaxError(e.getLocalizedMessage());
    }
    return new SpanOrQuery(sql.toArray(new SpanQuery[sql.size()]));
   // return new SpanNearQuery(sql.toArray(new SpanQuery[sql.size()]), params.getInt(OWLParams.SLOP, 10), true);//<co id="qqp.spanNear"/>
    /*
    <calloutlist>
        <callout arearefs="qqp.handleAT"><para>Add the AnswerType to the query</para></callout>
        <callout arearefs="qqp.addTerms"><para>Add the original query terms to the query</para></callout>
        <callout arearefs="qqp.spanNear"><para>Query the index looking for all of the parts near each other</para></callout>
    </calloutlist>
    */
    //<end id="qqp.query"/>
    
  }
  }
}
