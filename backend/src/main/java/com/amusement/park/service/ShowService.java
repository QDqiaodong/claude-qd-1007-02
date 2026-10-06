package com.amusement.park.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amusement.park.common.BizException;
import com.amusement.park.common.ParkShowTime;
import com.amusement.park.dto.ShowInput;
import com.amusement.park.dto.ShowListItem;
import com.amusement.park.entity.Show;
import com.amusement.park.repository.ShowRepository;

/**
 * 园区演出业务。
 *
 * <p>核心规则：预订座位不得超过演出容量（capacity），超出则抛出中文
 * {@link BizException} 拒绝，避免超售。</p>
 *
 * <p>演出时刻一律以园区固定时区（Asia/Shanghai）解释后存储与排序，
 * 规则见 {@link ParkShowTime}：不带偏移量的历史值按园区挂钟原样解释，
 * 带偏移量的值换算到园区时区；列表按解释后的真实时刻从早到晚排序，
 * 无时刻记录置末，时刻相同按演出编号、再按主键形成确定可重复的次序。</p>
 */
@Service
public class ShowService {

    private static final String OFFSET_BARE = "BARE";

    /**
     * 列表次序：有园区时刻者按时刻升序 → 演出编号升序 → 主键升序；
     * 无时刻者整体置末，内部同样按编号、主键排序。全部字段确定，任何环境结果相同。
     */
    private static final Comparator<Show> LIST_ORDER =
            Comparator.comparing(Show::getShowAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Show::getShowCode, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Show::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ShowRepository showRepository;
    private final Clock clock;

    public ShowService(ShowRepository showRepository, Clock clock) {
        this.showRepository = showRepository;
        this.clock = clock;
    }

    public List<ShowListItem> list() {
        return showRepository.findAll().stream()
                .sorted(LIST_ORDER)
                .map(ShowService::toListItem)
                .toList();
    }

    public ShowListItem get(Long id) {
        return toListItem(getEntity(id));
    }

    private Show getEntity(Long id) {
        return showRepository.findById(id)
                .orElseThrow(() -> new BizException("演出不存在：" + id));
    }

    private static ShowListItem toListItem(Show show) {
        ShowListItem item = new ShowListItem();
        item.setId(show.getId());
        item.setShowCode(show.getShowCode());
        item.setShowName(show.getShowName());
        item.setVenue(show.getVenue());
        item.setCapacity(show.getCapacity());
        item.setBooked(show.getBooked());
        item.setTimeSource(show.getTimeSource());
        item.setTimeOffset(show.getTimeOffset());
        LocalDateTime parkTime = show.getShowAt();
        item.setScheduled(parkTime != null);
        item.setShowAt(ParkShowTime.toStoredText(parkTime));
        item.setParkDate(ParkShowTime.parkDate(parkTime));
        item.setParkTime(ParkShowTime.parkClock(parkTime));
        item.setParkDateTime(ParkShowTime.parkDateTimeText(parkTime));
        return item;
    }

    @Transactional
    public ShowListItem create(ShowInput input) {
        if (input.getShowCode() == null || input.getShowCode().isBlank()) {
            throw new BizException("演出编号不能为空");
        }
        if (input.getShowName() == null || input.getShowName().isBlank()) {
            throw new BizException("演出名称不能为空");
        }
        if (input.getCapacity() == null || input.getCapacity() <= 0) {
            throw new BizException("演出容量必须大于 0");
        }
        Show show = new Show();
        show.setShowCode(input.getShowCode().trim());
        show.setShowName(input.getShowName().trim());
        show.setVenue(input.getVenue());
        show.setCapacity(input.getCapacity());
        show.setBooked(input.getBooked() == null ? 0 : Math.max(0, input.getBooked()));
        if (show.getBooked() > show.getCapacity()) {
            throw new BizException("初始预订 " + show.getBooked() + " 已超过容量 " + show.getCapacity());
        }
        applyShowAt(show, input.getShowAtRaw());
        show.setCreatedAt(LocalDateTime.now(clock));
        return toListItem(showRepository.save(show));
    }

    @Transactional
    public ShowListItem update(Long id, ShowInput input) {
        Show show = getEntity(id);
        if (input.getShowName() != null && !input.getShowName().isBlank()) {
            show.setShowName(input.getShowName().trim());
        }
        if (input.getVenue() != null) {
            show.setVenue(input.getVenue());
        }
        if (input.getCapacity() != null) {
            if (input.getCapacity() <= 0) {
                throw new BizException("演出容量必须大于 0");
            }
            if (show.getBooked() > input.getCapacity()) {
                throw new BizException("容量不能小于已预订座位数（已订 " + show.getBooked() + "）");
            }
            show.setCapacity(input.getCapacity());
        }
        // null=本次不提交该字段（不修改）；空串=显式清空时刻；非空串按园区时区规则解释
        if (input.getShowAtRaw() != null) {
            applyShowAt(show, input.getShowAtRaw());
        }
        return toListItem(showRepository.save(show));
    }

    /**
     * 按园区时区规则解释原始时刻串并落库，同时保留原始值与偏移量标记。
     * 空白串表示清空；无法解析时抛中文业务异常，原文随提示返回以便追溯。
     */
    private void applyShowAt(Show show, String raw) {
        LocalDateTime parkTime;
        try {
            parkTime = ParkShowTime.toParkTime(raw);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
        show.setShowAt(parkTime);
        if (parkTime == null) {
            show.setTimeSource(null);
            show.setTimeOffset(null);
        } else {
            show.setTimeSource(raw.trim());
            show.setTimeOffset(describeOffset(raw.trim()));
        }
    }

    /**
     * 从原始串提取偏移量标记用于追溯：不带偏移量返回 {@code BARE}；
     * 带偏移量返回规范化偏移文本（Z / +08:00 / -05:00 等）。
     */
    private static String describeOffset(String raw) {
        return ParkShowTime.carriesOffset(raw) ? ParkShowTime.normalizedOffset(raw) : OFFSET_BARE;
    }

    /**
     * 预订座位。超出容量则拒绝。
     *
     * @param seats 本次预订座位数
     */
    @Transactional
    public Show reserve(Long id, Integer seats) {
        Show show = getEntity(id);
        if (seats == null || seats <= 0) {
            throw new BizException("预订座位数必须大于 0");
        }
        int after = show.getBooked() + seats;
        if (after > show.getCapacity()) {
            throw new BizException("超出演出容量，剩余座位仅 "
                    + (show.getCapacity() - show.getBooked()) + " 个，无法再预订 " + seats + " 个");
        }
        show.setBooked(after);
        return showRepository.save(show);
    }
}
