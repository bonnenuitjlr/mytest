package com.mgauto.app.api;

import com.mgauto.app.dao.MgautoColumnClassificationDao;
import com.mgauto.app.entity.MgautoColumnClassificationEntity;
import com.mgauto.app.entity.MgautoCompEntity;
import com.mgauto.app.entity.MgautoGroupEntity;
import com.mgauto.app.entity.MgautoPageEntity;
import com.mgauto.app.service.*;
import com.mgauto.app.util.HttpUtilz;
import com.mgauto.app.vo.CompView;
import com.mgauto.app.vo.CompViewForPage;
import com.mgauto.app.vo.GroupView;
import com.mgauto.app.vo.PageView;
import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController()
@RequestMapping("api")
public class HomePagezApi {
	private static Logger log = LoggerFactory.getLogger(HomePagezApi.class);

	@GetMapping("/test")
	public R test(HttpServletRequest req) {
		Map<String, String> header = HttpUtilz.getRequestHeader(req);
		String userid = header.get("userid");
		String userId = header.get("userId");
		String UserId = header.get("UserId");
		String Userid = header.get("Userid");
		return R.ok().put("userid", userid).put("userId",userId).put("UserId",UserId).put("Userid",Userid);

	}

	@Autowired
	private PageService pageService;
	@Autowired
	private GroupService groupService;
	@Autowired
	private CompService compService;
	@Autowired
	private MgautoColumnClassificationDao columnClassificationDao;
	@Autowired
	private CompDatasService compDatasService;
	@Autowired
	private HomePageService homePageService;

	/**
	 * 获取首页按钮
	 * 
	 * @return
	 */
	@GetMapping("getColumnClassification")
	public R getColumn(HttpServletRequest req) throws Exception{
		Map<String, String> header = HttpUtilz.getRequestHeader(req);
		log.info("获取app的clientIp:"+header.get("clientip"));
		List<MgautoColumnClassificationEntity> lists = columnClassificationDao.queryAll();
		return R.ok().put("body", lists);
	}

	@Cacheable(value = "pageCache", key = "#pageId" ,unless="#result == null")
	@GetMapping("/getPage/{pageId}")
	public R getPage(@PathVariable("pageId") String pageId) throws Exception{
		long start = System.currentTimeMillis();
		// 获取page
		MgautoPageEntity page = pageService.queryById(pageId);
		if (page == null) {
			return R.error(1100, "page页面不存在");
		}
		PageView pageView = new PageView();


		BeanUtils.copyProperties(pageView, page);
		// 获取page下面的groups
		List<MgautoGroupEntity> groups = groupService.queryGroupsByPageId(pageId);
		// 获取group里面的comps
		if (groups != null && groups.size() > 0) {
			ArrayList<GroupView> grouplist = new ArrayList<>();

			for (int i = 0; i < groups.size(); i++) {
				GroupView groupView = new GroupView();
				BeanUtils.copyProperties(groupView, groups.get(i));

				List<MgautoCompEntity> comps = compService.queryCompsByGroupId(groups.get(i).getId());
				if (comps != null && comps.size() > 0) {
					ArrayList<CompViewForPage> complist = new ArrayList<>();
					for (int j = 0; j < comps.size(); j++) {
						CompViewForPage compView = new CompViewForPage();
						BeanUtils.copyProperties(compView, comps.get(j));
						complist.add(j, compView);
					}
					groupView.setComps(complist);
				}

				grouplist.add(i, groupView);
			}


			pageView.setGroups(grouplist);
		}


		return R.ok().put("body", pageView);
	}

	@Cacheable(value = "groupCache", key = "#groupId.concat(#type+'-').concat(#programType+'-').concat(#programTypeV2+'-')" ,unless="#result == null")
	@GetMapping("/getGroup/{groupId}")
	public R getGroup(@PathVariable("groupId") String groupId , HttpServletRequest req ,String type ,String programType ,String programTypeV2) throws Exception{
		log.info("获取group----begin----- 接收参数：  groupId:{}  type:{}  programType:{}  programTypeV2:{}",groupId,type,programType,programTypeV2);
		long start = System.currentTimeMillis();

		MgautoGroupEntity group = groupService.queryById(groupId);
		if (null==group){
			return R.error(1004,"根据id查找的group不存在");
		}

		GroupView groupView = new GroupView();

		BeanUtils.copyProperties(groupView, group);

		List<MgautoCompEntity> comps = compService.queryCompsByGroupId(group.getId());
		if (comps != null && comps.size() > 0) {
			// 获取请求头
			Map<String, String> header = HttpUtilz.getRequestHeader(req);
			
			int requestDataCount = 0;
			ArrayList<CompView> complist = new ArrayList<>();
			for (int j = 0; j < comps.size(); j++) {
				//处理comp 数据
				long s1 = System.currentTimeMillis();
				CompView compView = homePageService.dealComp2CompView(comps.get(j), header, group.getGroupStyle() , type ,programType, programTypeV2,requestDataCount);
//					log.info("===== compView:"+compView.toString());
				if(compView!=null && compView.getDataCount()!=null &&compView.getRequestDataCount()!=null){
					requestDataCount = compView.getRequestDataCount();
				}
				
				long s2 = System.currentTimeMillis();
				log.info("获取comp数据所用时间："+(s2-s1));
				complist.add(j, compView);
				
				
			}
			groupView.setComps(complist);
		}

		log.info("获取group----end");
		long end = System.currentTimeMillis();
		log.info("获取group所用时间："+(end - start));
		return R.ok().put("body", groupView);

	}

	@Cacheable(value = "compCache", key = "#compId.concat(#groupStyle+'-').concat(#type+'-').concat(#programType+'-').concat(#programTypeV2+'-')" ,unless="#result == null")
	@GetMapping("/getComp/{compId}")
	public R getComp(@PathVariable("compId") String compId, HttpServletRequest req, String groupStyle ,String type ,String programType,String programTypeV2) throws Exception{
		log.info("获取comp----begin----- 接收参数：  compId:{}  type:{}  programType:{}  programTypeV2:{}",compId,type,programType,programTypeV2);
		MgautoCompEntity comp = compService.queryById(compId);
		// 获取请求头
		Map<String, String> header = HttpUtilz.getRequestHeader(req);
		CompView compView = homePageService.dealComp2CompView(comp,header,groupStyle,type,programType, programTypeV2,0);
		log.info("获取comp----end");
		return R.ok().put("body", compView);

	}

}
