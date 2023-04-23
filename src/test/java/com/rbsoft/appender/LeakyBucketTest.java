package com.rbsoft.appender;

class LeakyBucketTest {

    //정의 매 1초마다 최대 N건을 전송한다.
    //1초보다 더 많이 소요될 수 있다.
    //전송 작업이 1초 미만으로 소요될 때, 각 전송의

    //만약 batchSize=1000, durationMillis=1초인데, 1초에 500건밖에 처리를 못하는 상황이면?
    //=> 매번 새로운 배치로 전송하게 된다. 즉, 전송률이 그렇게 나오지 않게 된다.
    //TODO. 1초에 최대 N개 처리하는지 여부
    void drains10Records() {

    }

    //TODO. BATCH_SIZE만큼 모두 처리했으면 나머지는 sleep한다.
}
