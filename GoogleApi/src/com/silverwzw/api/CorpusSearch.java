package com.silverwzw.api;

import gate.Corpus;
import gate.Document;
import gate.Factory;
import gate.Gate;
import gate.corpora.DocumentImpl;
import gate.creole.ResourceInstantiationException;
import gate.util.GateException;

import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.silverwzw.Debug;

public class CorpusSearch extends AbstractSearch {
	private Search se;
	private boolean sync = true;
	CorpusSearch(Search searchEngine) {
		se = searchEngine;
	}
	public void setSearchTerm(String searchTerm) {
		se.setSearchTerm(searchTerm);
	}
	public List<String> asUrlStringList(int i) {
		return se.asUrlStringList(i);
	}
	public void setSync(boolean syncValue) {
		sync = syncValue;
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
