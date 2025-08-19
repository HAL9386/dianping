package com.dp.controller;


import com.dp.dto.Result;
import com.dp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/follow")
public class FollowController {
  private final IFollowService followService;

  public FollowController(IFollowService followService) {
    this.followService = followService;
  }

  @PutMapping("/{id}/{isFollow}")
  public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
    return followService.follow(followUserId, isFollow);
  }

  @GetMapping("/or/not/{id}")
  public Result isFollow(@PathVariable("id") Long followUserId) {
    return followService.isFollow(followUserId);
  }

  @GetMapping("/common/{id}")
  public Result followCommon(@PathVariable("id") Long checkUserId) {
    return followService.followCommon(checkUserId);
  }

}
