package com.pixload.app;
import static org.junit.Assert.*;
import java.util.*; import java.util.concurrent.*; import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
/** Verifier-derived probes for S1/S3/S5/S6 at the pure queue-book level. */
public class QueueProbeTest {
  private static DownloadQueueBook.Item item(String id,String s,int p,int m){return new DownloadQueueBook.Item(id,"Obra "+id,"",s,p,m);}
  private static DownloadQueueBook bookOf(DownloadQueueBook.Item... items){DownloadQueueBook b=new DownloadQueueBook();List<DownloadQueueBook.Item> r=new ArrayList<>();for(DownloadQueueBook.Item i:items)r.add(i);b.load(r);return b;}
  @Test public void capCountsOnlyQueuedAndActive(){
    DownloadQueueBook b=bookOf(item("a",DownloadQueueBook.ACTIVE,10,100),item("b",DownloadQueueBook.QUEUED,0,0),
      item("err",DownloadQueueBook.ERROR,0,0),item("dead",DownloadQueueBook.INTERRUPTED,40,100));
    assertEquals(2,b.count()); assertFalse(b.isFull());
    for(int i=0;i<3;i++) assertTrue(b.add("p"+i,"Obra p"+i,""));
    assertTrue(b.isFull()); assertFalse(b.add("overflow","Obra overflow","")); assertEquals(5,b.count());}
  @Test public void finishFreesExactlyOneSlot(){
    DownloadQueueBook b=new DownloadQueueBook();
    for(int i=0;i<DownloadQueueBook.LIMIT;i++) assertTrue(b.add("j"+i,"Obra "+i,""));
    assertTrue(b.remove("j2")); assertEquals(4,b.count()); assertFalse(b.remove("j2"));
    assertEquals(4,b.count()); assertTrue(b.add("j5","Obra 5","")); assertFalse(b.add("j6","Obra 6",""));}
  @Test public void failedJobReleasesSlotButStaysListed(){
    DownloadQueueBook b=new DownloadQueueBook();
    for(int i=0;i<DownloadQueueBook.LIMIT;i++) b.add("j"+i,"Obra "+i,"");
    assertTrue(b.state("j0",DownloadQueueBook.ERROR,0,0)); assertEquals(4,b.count());
    assertTrue(b.add("j5","Obra 5","")); assertEquals(6,b.items().size());
    assertEquals(DownloadQueueBook.ERROR,b.items().get(0).state);}
  @Test public void fifoOrderSurvivesStateChanges(){
    DownloadQueueBook b=new DownloadQueueBook(); b.add("first","Obra 1",""); b.add("second","Obra 2",""); b.add("third","Obra 3","");
    b.state("first",DownloadQueueBook.ACTIVE,0,0);
    assertEquals("first",b.pending().get(0).id); assertEquals("second",b.pending().get(1).id); assertEquals("third",b.pending().get(2).id);}
  @Test public void cancellingOneDoesNotDisturbTheOthers(){
    DownloadQueueBook b=new DownloadQueueBook(); b.add("a","Obra a",""); b.add("b","Obra b",""); b.add("c","Obra c","");
    b.state("b",DownloadQueueBook.ACTIVE,40,100); assertTrue(b.remove("b"));
    List<DownloadQueueBook.Item> p=b.pending(); assertEquals(2,p.size());
    assertEquals("a",p.get(0).id); assertEquals("c",p.get(1).id);}
  @Test public void aggregatePercentBoundaries(){
    assertEquals(0,new DownloadQueueBook().percent());
    DownloadQueueBook b=bookOf(item("a",DownloadQueueBook.ACTIVE,0,0),item("b",DownloadQueueBook.QUEUED,100,100));
    assertEquals(50,b.percent()); b.progress("a",250,100); assertEquals(100,b.percent());
    b.remove("a"); b.remove("b"); assertEquals(0,b.percent());}
  @Test public void duplicateAndEmptyIds(){
    DownloadQueueBook b=new DownloadQueueBook(); assertFalse(b.add("","Obra","")); assertTrue(b.add("same","Obra",""));
    assertFalse(b.add("same","Obra","")); b.remove("same"); assertTrue(b.add("same","Obra","")); assertEquals(1,b.count());}
  @Test public void concurrentAddsRespectTheCap() throws Exception {
    DownloadQueueBook b=new DownloadQueueBook(); int n=16; CountDownLatch s=new CountDownLatch(1),d=new CountDownLatch(n);
    AtomicInteger ok=new AtomicInteger();
    for(int t=0;t<n;t++){final int id=t;new Thread(()->{try{s.await();for(int i=0;i<20;i++) if(b.add("t"+id+"-"+i,"Obra","")) ok.incrementAndGet();}catch(InterruptedException e){}finally{d.countDown();}}).start();}
    s.countDown(); d.await();
    assertEquals(DownloadQueueBook.LIMIT,ok.get()); assertEquals(DownloadQueueBook.LIMIT,b.count()); assertEquals(DownloadQueueBook.LIMIT,b.items().size());}
}
