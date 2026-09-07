package com.campus.ai.tool;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 校历查询工具：LLM 可在学生询问学期日期、考试周、假期等信息时主动调用。
 *
 * <p>当前返回静态数据；生产环境中应对接数据库或外部日历 API。
 *
 * <p>如果没有这个文件：
 * <ul>
 *   <li>LLM 无法主动查询校历信息，只能依赖知识库中可能过时的文档</li>
 *   <li>"什么时候考试""寒假放几天"等问题的回答准确度会下降</li>
 *   <li>工具调用能力形同虚设，CampusRagAssistant 的 tools 配置失去意义</li>
 * </ul>
 */
@Slf4j
@Component
public class CalendarTool {

    @Tool("查询校历信息：学期开始/结束日期、考试周、寒暑假时间。参数 semester 为 'current'（当前学期）、'next'（下学期）或具体如 '2025-2026-1'。")
    public String queryCalendar(String semester) {
        log.info("Tool call: queryCalendar(semester={})", semester);
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        // 判断当前是哪个学期
        String currentSemester;
        String currentDates;
        if (month >= 9) {
            currentSemester = year + "-" + (year + 1) + "-1";
            currentDates = String.format("秋季学期：%d年9月1日 ~ %d年1月15日", year, year + 1);
        } else if (month >= 2 && month <= 7) {
            currentSemester = (year - 1) + "-" + year + "-2";
            currentDates = String.format("春季学期：%d年2月25日 ~ %d年7月10日", year, year);
        } else {
            currentSemester = year + "-" + (year + 1) + "-1";
            currentDates = "寒假期间，下学期 " + currentSemester + " 预计9月1日开学";
        }

        String examWeek;
        if (month >= 9 || month <= 1) {
            examWeek = String.format("考试周：第16-17周（约%d年12月下旬~%d年1月上旬）", year, year + 1);
        } else {
            examWeek = String.format("考试周：第16-17周（约%d年6月下旬~7月上旬）", year);
        }

        if ("current".equals(semester) || semester == null) {
            return String.format("当前学期：%s\n%s\n%s\n寒假：约1月16日~2月24日\n暑假：约7月11日~8月31日",
                    currentSemester, currentDates, examWeek);
        } else if ("next".equals(semester)) {
            if (month >= 9) {
                return String.format("下学期：%d-%d-2（春季）\n春季学期：%d年2月25日 ~ %d年7月10日\n考试周：第16-17周（约6月下旬~7月上旬）",
                        year, year + 1, year + 1, year + 1);
            } else {
                return String.format("下学期：%d-%d-1（秋季）\n秋季学期：%d年9月1日 ~ %d年1月15日\n考试周：第16-17周（约12月下旬~1月上旬）",
                        year, year + 1, year, year + 1);
            }
        }
        // 查询特定学期
        return String.format("学期 %s 的具体校历信息暂未公布，请关注教务处通知。\n参考：%s\n%s",
                semester, currentDates, examWeek);
    }

    @Tool("查询考试安排相关时间：期末考试周、补考时间、四六级考试时间。")
    public String queryExamSchedule(String examType) {
        log.info("Tool call: queryExamSchedule(type={})", examType);
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        if ("期末".equals(examType) || "final".equals(examType)) {
            if (month >= 9 || month <= 1) {
                return String.format("本学期期末考试预计在第16-17周（%d年12月下旬~%d年1月上旬），具体安排请关注教务处通知。", year, year + 1);
            }
            return String.format("本学期期末考试预计在第16-17周（%d年6月下旬~7月上旬），具体安排请关注教务处通知。", year);
        }
        if ("四六级".equals(examType) || "cet".equals(examType)) {
            return String.format("CET-4/6 考试通常在每年6月和12月的第三个周六举行。%d年预计时间：6月%d日、12月%d日。报名请关注教务处通知。",
                    year, 15 + (6 - LocalDate.of(year, 6, 1).getDayOfWeek().getValue()) % 7,
                    15 + (12 - LocalDate.of(year, 12, 1).getDayOfWeek().getValue()) % 7);
        }
        return "常见考试时间：\n- 期末考试：每学期第16-17周\n- 补考：下学期开学前两周\n- 四六级：每年6月和12月\n- 计算机等级考试：每年3月和9月\n具体安排请关注教务处通知。";
    }
}
