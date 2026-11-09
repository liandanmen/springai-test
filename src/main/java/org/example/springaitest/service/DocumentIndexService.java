package org.example.springaitest.service;

import org.example.springaitest.dto.KbAskResponse;
import org.example.springaitest.dto.KbIndexResult;
import org.example.springaitest.dto.KbSearchHit;

import java.util.List;

public interface DocumentIndexService {
    KbIndexResult index(Long userId, String fileName, String rawContent);
    List<KbSearchHit> search(Long userId, String question);


    KbAskResponse ask(Long userId, String hits);
}


