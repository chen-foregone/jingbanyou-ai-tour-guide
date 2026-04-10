package cn.edu.gdou.jingbanyou.tourist.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tourist")
public class ChatController {

    @GetMapping
    public String audioChat(){

    }
}
