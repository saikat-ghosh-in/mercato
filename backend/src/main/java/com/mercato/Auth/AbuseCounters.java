package com.mercato.Auth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;import java.time.Instant;
@Service @RequiredArgsConstructor
public class AbuseCounters {
 private final AbuseBucketRepository buckets; private final JdbcTemplate jdbc;private final LoginFailureRepository failures;
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public int consume(String namespace,String identifier,int limit,int windowSeconds,boolean progressive) {
  String key=AuthHash.digest(namespace+":"+identifier); Instant now=Instant.now();
  jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c->{String sql=c.getMetaData().getDatabaseProductName().equals("PostgreSQL")?"insert into auth_abuse_buckets(bucket_key,window_start,attempts) values (?, ?, 0) on conflict (bucket_key) do nothing":"merge into auth_abuse_buckets a using (values (cast(? as varchar),cast(? as timestamp with time zone))) s(k,t) on a.bucket_key=s.k when not matched then insert (bucket_key,window_start,attempts) values(s.k,s.t,0)";try(var st=c.prepareStatement(sql)){st.setString(1,key);st.setTimestamp(2,Timestamp.from(now));st.executeUpdate();}return null;});
  var bucket=buckets.locked(key).orElseThrow();
  if(bucket.getWindowStart().plusSeconds(windowSeconds).isBefore(now)){bucket.setWindowStart(now);bucket.setAttempts(0);bucket.setBlockedUntil(null);}
  if(bucket.getBlockedUntil()!=null&&bucket.getBlockedUntil().isAfter(now)) return (int)Math.max(1,bucket.getBlockedUntil().getEpochSecond()-now.getEpochSecond());
  bucket.setAttempts(bucket.getAttempts()+1);
  int delay=bucket.getAttempts()>limit?(progressive?Math.min(900,1<<Math.min(9,bucket.getAttempts()-limit)):Math.max(1,(int)(bucket.getWindowStart().plusSeconds(windowSeconds).getEpochSecond()-now.getEpochSecond()))):0;
  if(delay>0)bucket.setBlockedUntil(now.plusSeconds(delay));return delay;
 }
 @Transactional(readOnly=true) public int retryAfter(String namespace,String identifier){return buckets.findById(AuthHash.digest(namespace+":"+identifier)).filter(b->b.getBlockedUntil()!=null&&b.getBlockedUntil().isAfter(Instant.now())).map(b->(int)Math.max(1,b.getBlockedUntil().getEpochSecond()-Instant.now().getEpochSecond())).orElse(0); }
 @Transactional(readOnly=true) public boolean requiresStepUp(String user){return failures.countByAccountHashAndFailedAtAfter(AuthHash.digest(user),Instant.now().minusSeconds(3600))>=50;}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void failed(String user){var failure=new LoginFailure();failure.setId(java.util.UUID.randomUUID().toString());failure.setAccountHash(AuthHash.digest(user));failure.setFailedAt(Instant.now());failures.save(failure);}
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=3600000) @Transactional public void pruneFailures(){failures.deleteByFailedAtBefore(Instant.now().minusSeconds(7200));}
}
