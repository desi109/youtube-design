package com.desi.youtube.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

  @GetMapping({ "/watch/{id}", "/upload" })
  public String app() {
    return "forward:/index.html";
  }
}
