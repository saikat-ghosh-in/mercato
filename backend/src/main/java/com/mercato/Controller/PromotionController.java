package com.mercato.Controller;
import com.mercato.Service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class PromotionController {
    private final PromotionService service;
    @GetMapping("/api/seller/promotions") public Object workspace(){return service.workspace();}
    @PostMapping("/api/seller/promotions") public Object book(@Valid @RequestBody PromotionService.Request request){return service.book(request);}
    @PostMapping("/api/seller/promotions/{id}/cancel") public Object cancel(@PathVariable Long id){return service.cancel(id);}
    @GetMapping("/api/public/promotions") public Object placements(@RequestParam(defaultValue="home") String placement,@RequestParam(required=false) String category,@RequestParam(required=false) String keyword){return service.placements(placement,category,keyword);}
}
