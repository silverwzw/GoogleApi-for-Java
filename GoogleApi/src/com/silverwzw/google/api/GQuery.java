package com.silverwzw.google.api;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;

import com.silverwzw.Debug;
import com.silverwzw.JSON.JSON;
import com.silverwzw.JSON.JSON.JsonStringFormatException;

import gate.Corpus;
import gate.Document;
import gate.Factory;
import gate.Gate;
import gate.corpora.DocumentImpl;
import gate.creole.ResourceInstantiationException;
import gate.util.GateException;

public class GQuery {
	private String q;
	private int i = 10;
	private String apiKey = "AIzaSyAxdsUVjbxnEV9FAfmK_5M9a2spo-uFL9g";
	private String seID = "016567116349354999812:g_wwmaarfsa";
	private boolean sync = false;
	
	public GQuery(String queryString) {
		Debug.into(this, "<Constrcutor> : new query:" + queryString);
		q = queryString;
		Debug.out(this, "<Constrcutor>");
	}
	
	final public void setSync(boolean isSync) {
		sync = isSync;
	}
	
	final public void setApiKey(String key) {
		apiKey = key;
	}
	
	final public void setSearchEngineID(String seID) {
		this.seID = seID;
	}
	
	final public void setResultPerPage (int resultNumPerPage) {
		if (resultNumPerPage < 1 || resultNumPerPage > 99) {
			System.err.println("Number of Results Per Page should greater than 1 and less than 99, use 30 instead");
			i = 10;
		} else {
			i = resultNumPerPage;
		}
	}
	
	final private String getGQURL(int startIndex) {
		if (startIndex <= 1) {
			return "https://www.googleapis.com/customsearch/v1?key=" + apiKey + "&cx=" + seID + "&q=" + q + "&num=" + i +"&alt=json";
		} else {
			return "https://www.googleapis.com/customsearch/v1?key=" + apiKey + "&cx=" + seID + "&q=" + q + "&num=" + i + "&start=" + startIndex + "&alt=json";
		}
	}
	
	final public List<String> asUrlStringList(int docNum) {
		Debug.into(this, "asUrlStringList");
		if (docNum < 1) {
			System.err.println("Number of Documents should greater than 1, use 20 instead");
			docNum = 20;
		}
		
		JSON[] qpage;
		int pageNum, cpagei;
		CountDownLatch threadSignal;
		List<String> uList;
		
		uList = new ArrayList<String>(docNum);
		pageNum = (docNum%i == 0) ? (docNum / i) : (docNum / i +1);
		qpage = new JSON[pageNum];

		Debug.println(2, "fetching Google Query Result page");
		
		if (!sync) {
			threadSignal = new CountDownLatch(pageNum);
			
			for (cpagei = 0; cpagei < pageNum; cpagei++) {
				new Thread(new _GetGQPage(threadSignal,getGQURL(cpagei * i + 1), qpage, cpagei)).start();
			}
			try {
				threadSignal.await();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			
			}
		} else {
			for (cpagei = 0; cpagei < pageNum; cpagei++) {
				new _GetGQPage(null, getGQURL(cpagei * i + 1), qpage, cpagei).run();
			}
		}
		
		Debug.println(2, "Parsing Google Query Result page");
		for (JSON json : qpage) {
			if (json == null || json.get("error") != null) {
				System.err.println("Query failed : " + ((json == null) ? "json == null" : "json.error != null"));
				continue;
			}
			try {
				for (Entry<String,JSON> el : json.get("items")) {
					if (docNum > 0) {
						String link = (String)el.getValue().get("link").toObject();
							uList.add(link);
							docNum --;
					}
					if (docNum <= 0) {
						break;
					}
				}
			} catch (RuntimeException ex) {
				System.out.print(json.format());
				throw ex;
			}
			if (docNum <= 0) {
				break;
			}
		}
		Debug.out(this, "asUrlStringList");
		return uList;
	}
	final public List<URL> asUrlList(int docNum) {
		List<URL> ul = new LinkedList<URL>();
		for (String u : asUrlStringList(docNum)) {
			try{
				ul.add(new URL(u));
			} catch (MalformedURLException e) {
				System.err.println("Cannot recognize the link returned by Google Query:" + u);
			}
		}
		return ul;
	}
	final public Corpus asCorpus(int docNum, String CorpusName) {
		Debug.into(this, "asCorpus");
		if (!Gate.isInitialised()) {
			System.err.println("GQuery.asCoprus: Gate not initialised! trying to initialize gate with default value.");
			try {
				Gate.init();
			} catch (GateException e) {
				throw new RuntimeException(e);
			}
		}
		
		List<URL> uList;
		uList = asUrlList(docNum);
		CountDownLatch threadSignal;
		Corpus corpus;
		Debug.println(2, "Buildiung Corpus");
		try {
			corpus =  Factory.newCorpus(CorpusName);
		} catch (ResourceInstantiationException ex) {
			throw new RuntimeException(ex); 
		}
		if (!sync) {
			threadSignal = new CountDownLatch(uList.size());
			for (URL u : uList) {
				new Thread(new _GetGateDoc(threadSignal,u,corpus)).start();
			}
			try {
				threadSignal.await();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		} else {
			for (URL u : uList) {
				new _GetGateDoc(null, u,corpus).run();
			}
		}
		Debug.out(this, "asCorpus");
		return corpus;
	}
}

class _GetGQPage implements Runnable {
	JSON[] pArr;
	int index;
	CountDownLatch countDownSig;
	String q;
	
	public _GetGQPage(CountDownLatch countDownSig, String queryURL, JSON[] pArr, int index) {
		this.pArr = pArr;
		this.index = index;
		this.countDownSig = countDownSig;
		q = queryURL;
	}
	public void run() {
		Debug.println(3, "Thread " + Thread.currentThread().getId() + ": query Google on: " + q);
		try {
			URLConnection conn = null;
			pArr[index] = null;
			try {
				conn = (new URL(q)).openConnection();
				Debug.println(3, "Thread " + Thread.currentThread().getId() + ": parsing  Google result page: " + q);
				pArr[index] = JSON.parse(conn.getInputStream());
			} catch (MalformedURLException e) {
				System.err.println("Google Query URL Exception! URL:" + q);
			} catch (IOException e) {
				System.err.println("IO Exception while Query Google on url:" + q);
			} catch (JsonStringFormatException e) {
				System.err.println("JSON format exception while Query Google on url:" + q);
			}
		} finally {
			if (countDownSig != null) {
				countDownSig.countDown();
			}
		}
	}
}

class _GetGateDoc implements Runnable {
	URL u;
	CountDownLatch countDownSig;
	Corpus corpus;
	_GetGateDoc(CountDownLatch countDownSig, URL u, Corpus corpus) {
		this.u = u;
		this.countDownSig = countDownSig; 
		this.corpus = corpus;
	}
	public void run() {
		try {
			Debug.println(3, "Thread " + Thread.currentThread().getId() + ": change local url to gate.Document : " + u);
			Document doc = new DocumentImpl();
			doc.setSourceUrl(u);
			try {
				doc.init();
			} catch (ResourceInstantiationException e) {
				System.err.println("Error while change url to gate.Document! url=" + u);
				doc = null;
			}
			Debug.println(3, "Thread " + Thread.currentThread().getId() + ": add document " + u + "to Corpus: " + corpus.getName());
			synchronized(corpus) {
				corpus.add(doc);
			}
		} finally {
			if (countDownSig != null) {
				countDownSig.countDown();
			}
		}
	}
}