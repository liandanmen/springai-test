package org.example.springaitest.service;

import org.example.springaitest.dto.KbAskResponse;
import org.example.springaitest.dto.KbDocumentVO;
import org.example.springaitest.dto.KbIndexResult;
import org.example.springaitest.dto.KbSearchHit;
import reactor.core.publisher.Flux;

import java.util.List;

public interface DocumentIndexService {
    KbIndexResult index(Long userId, String fileName, String rawContent);
    List<KbSearchHit> search(Long userId, String question);
    KbAskResponse ask(Long userId, String hits);
    Flux<String> askStream(String question, List<KbSearchHit> hits);
    List<KbDocumentVO> list(Long userId);
    String delete(Long userId,Long documentId);
}


