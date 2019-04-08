package me.leoko.advancedban.commands;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.leoko.advancedban.AdvancedBan;
import me.leoko.advancedban.AdvancedBanCommandSender;
import me.leoko.advancedban.AdvancedBanLogger;
import me.leoko.advancedban.manager.DatabaseManager;
import me.leoko.advancedban.manager.MessageManager;
import me.leoko.advancedban.manager.UUIDManager;
import me.leoko.advancedban.punishment.Punishment;
import me.leoko.advancedban.punishment.PunishmentManager;
import me.leoko.advancedban.punishment.PunishmentType;
import me.leoko.advancedban.utils.SQLQuery;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static me.leoko.advancedban.commands.CommandUtils.*;
import static me.leoko.advancedban.commands.CommandUtils.processName;

public enum Command {
    BAN(
            PunishmentType.BAN.getPerms(),
            ".+",
            new PunishmentCommand(PunishmentType.BAN),
            PunishmentType.BAN.getConfSection("Usage"),
            "ban"),

    TEMP_BAN(
            PunishmentType.TEMP_BAN.getPerms(),
            "\\S+ [1-9][0-9]*([wdhms]|mo)( .*)?",
            new PunishmentCommand(PunishmentType.TEMP_BAN),
            PunishmentType.TEMP_BAN.getConfSection("Usage"),
            "tempban"),

    IP_BAN(
            PunishmentType.IP_BAN.getPerms(),
            ".+",
            new PunishmentCommand(PunishmentType.IP_BAN),
            PunishmentType.IP_BAN.getConfSection("Usage"),
            "ipban", "banip", "ban-ip"),

    MUTE(
            PunishmentType.MUTE.getPerms(),
            ".+",
            new PunishmentCommand(PunishmentType.MUTE),
            PunishmentType.MUTE.getConfSection("Usage"),
            "mute"),

    TEMP_MUTE(
            PunishmentType.TEMP_MUTE.getPerms(),
            "\\S+ [1-9][0-9]*([wdhms]|mo)( .*)?",
            new PunishmentCommand(PunishmentType.TEMP_MUTE),
            PunishmentType.TEMP_MUTE.getConfSection("Usage"),
            "tempmute"),

    WARN(
            PunishmentType.WARNING.getPerms(),
            ".+",
            new PunishmentCommand(PunishmentType.WARNING),
            PunishmentType.WARNING.getConfSection("Usage"),
            "warn"),

    TEMP_WARN(
            PunishmentType.TEMP_WARNING.getPerms(),
            "\\S+ [1-9][0-9]*([wdhms]|mo)( .*)?",
            new PunishmentCommand(PunishmentType.TEMP_WARNING),
            PunishmentType.TEMP_WARNING.getConfSection("Usage"),
            "tempwarn"),

    KICK(
            PunishmentType.KICK.getPerms(),
            ".+",
            input -> {
                if (!AdvancedBan.get().isOnline(input.getPrimaryData())) {
                    input.getSender().sendCustomMessage("Kick.NotOnline", true,
                            "NAME", input.getPrimary());
                    return;
                }

                new PunishmentCommand(PunishmentType.KICK).accept(input);
            },
            PunishmentType.KICK.getConfSection("Usage"),
            "kick"),

    UN_BAN("ab." + PunishmentType.BAN.getName() + ".undo",
            "\\S+",
            new RevokePunishmentCommand(PunishmentType.BAN),
            "Un" + PunishmentType.BAN.getConfSection(".Usage"),
            "unban"),

    UN_MUTE("ab." + PunishmentType.MUTE.getName() + ".undo",
            "\\S+",
            new RevokePunishmentCommand(PunishmentType.MUTE),
            "Un" + PunishmentType.MUTE.getConfSection(".Usage"),
            "unmute"),

    UN_WARN("ab." + PunishmentType.WARNING.getName() + ".undo",
            "[0-9]+|(?i:clear \\S+)",
            input -> {
                final String confSection = PunishmentType.WARNING.getConfSection();
                if (input.getPrimaryData().equals("clear")) {
                    input.next();
                    String name = input.getPrimary();
                    UUID uuid = processName(input);
                    if (uuid == null)
                        return;

                    List<Punishment> punishments = PunishmentManager.getInstance().getWarns(uuid);
                    if (!punishments.isEmpty()) {
                        input.getSender().sendCustomMessage("Un" + confSection + ".Clear.Empty",
                                true, "NAME", name);
                        return;
                    }

                    String operator = input.getSender().getName();
                    for (Punishment punishment : punishments) {
                        //TODO broadcast
                        PunishmentManager.getInstance().deletePunishment(punishment);
                    }
                    input.getSender().sendCustomMessage("Un" + confSection + ".Clear.Done",
                            true, "COUNT", String.valueOf(punishments.size()));
                } else {
                    new RevokePunishmentIDCommand("Un" + confSection, PunishmentManager.getInstance()::getWarn).accept(input);
                }
            },
            "Un" + PunishmentType.WARNING.getConfSection(".Usage"),
            "unwarn"),

    UN_PUNISH("ab.all.undo",
            "[0-9]+",
            new RevokePunishmentIDCommand("UnPunish", PunishmentManager.getInstance()::getPunishment),
            "UnPunish.Usage",
            "unpunish"),

    CHANGE_REASON("ab.changeReason",
            "([0-9]+|(ban|mute) \\S+) .+",
            input -> {
                Optional<Punishment> punishment;

                if (input.getPrimaryData().matches("[0-9]*")) {
                    input.next();
                    int id = Integer.parseInt(input.getPrimaryData());

                    punishment = PunishmentManager.getInstance().getPunishment(id);
                } else {
                    PunishmentType type = PunishmentType.valueOf(input.getPrimary());
                    input.next();

                    Object target;
                    if (!input.getPrimary().matches("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")) {
                        target = processName(input);
                        if (target == null)
                            return;
                    } else {
                        try {
                            target = InetAddress.getByName(input.getPrimary());
                        } catch (UnknownHostException e) {
                            AdvancedBanLogger.getInstance().logException(e);
                            return;
                        }
                        input.next();
                    }

                    punishment = getPunishment(target, type);
                }

                String reason = processReason(input);
                if (reason == null)
                    return;

                if (punishment.isPresent()) {
                    punishment.get().setReason(reason);
                    PunishmentManager.getInstance().updatePunishment(punishment.get());
                    input.getSender().sendCustomMessage("ChangeReason.Done",
                            true, "ID", punishment.get().getId());
                } else {
                    input.getSender().sendCustomMessage("ChangeReason.NotFound", true);
                }
            },
            "ChangeReason.Usage",
            "change-reason"),

    BAN_LIST("ab.banlist",
            "([1-9][0-9]*)?",
            new ListCommand(
                    target -> PunishmentManager.getInstance().getPunishments(SQLQuery.SELECT_ALL_PUNISHMENTS_LIMIT, 150),
                    "Banlist", false, false),
            "Banlist.Usage",
            "banlist"),

    HISTORY("ab.history",
            "\\S+( [1-9][0-9]*)?",
            new ListCommand(
                    target -> PunishmentManager.getInstance().getPunishments(target, null, false),
                    "History", true, true),
            "Banlist.Usage",
            "history"),

    WARNS(null,
            "\\S+( [1-9][0-9]*)?|\\S+",
            input -> {
                if (input.getPrimary().matches("\\S+")) {
                    if (!Universal.get().getMethods().hasPerms(input.getSender(), "ab.warns.other")) {
                        MessageManager.sendMessage(input.getSender(), "General.NoPerms", true);
                        return;
                    }

                    new ListCommand(
                            target -> PunishmentManager.get().getPunishments(target, null, false),
                            "Warns", false, true).accept(input);
                } else {
                    if (!Universal.get().getMethods().hasPerms(input.getSender(), "ab.warns.own")) {
                        MessageManager.sendMessage(input.getSender(), "General.NoPerms", true);
                        return;
                    }

                    String name = Universal.get().getMethods().getName(input.getSender());
                    new ListCommand(
                            target -> PunishmentManager.get().getPunishments(name, null, false),
                            "Warns", false, false).accept(input);
                }
            },
            "Warns.Usage",
            "warns"),

    CHECK("ab.check",
            "\\S+",
            input -> {
                String name = input.getPrimary();

                String uuid = processName(input);
                if (uuid == null)
                    return;


                String ip = Universal.get().getIps().getOrDefault(name.toLowerCase(), "none cashed");
                String loc = Universal.get().getMethods().getFromUrlJson("http://ip-api.com/json/" + ip, "country");
                Punishment mute = PunishmentManager.get().getMute(uuid);
                Punishment ban = PunishmentManager.get().getBan(uuid);

                Object sender = input.getSender();
                MessageManager.sendMessage(sender, "Check.Header", true, "NAME", name);
                MessageManager.sendMessage(sender, "Check.UUID", false, "UUID", uuid);
                if (Universal.get().hasPerms(sender, "ab.check.ip")) {
                    MessageManager.sendMessage(sender, "Check.IP", false, "IP", ip);
                }
                MessageManager.sendMessage(sender, "Check.Geo", false, "LOCATION", loc == null ? "failed!" : loc);
                MessageManager.sendMessage(sender, "Check.Mute", false, "DURATION", mute == null ? "§anone" : mute.getType().isTemp() ? "§e" + mute.getDuration(false) : "§cperma");
                if (mute != null) {
                    MessageManager.sendMessage(sender, "Check.MuteReason", false, "REASON", mute.getReason());
                }
                MessageManager.sendMessage(sender, "Check.Ban", false, "DURATION", ban == null ? "§anone" : ban.getType().isTemp() ? "§e" + ban.getDuration(false) : "§cperma");
                if (ban != null) {
                    MessageManager.sendMessage(sender, "Check.BanReason", false, "REASON", ban.getReason());
                }
                MessageManager.sendMessage(sender, "Check.Warn", false, "COUNT", PunishmentManager.get().getCurrentWarns(uuid) + "");
            },
            "Check.Usage",
            "check"),

    SYSTEM_PREFERENCES("ab.systemprefs",
            ".*",
            input -> {
                MethodInterface mi = Universal.get().getMethods();
                Calendar calendar = new GregorianCalendar();
                Object sender = input.getSender();
                mi.sendMessage(sender, "§c§lAdvancedBan v2 §cSystemPrefs");
                mi.sendMessage(sender, "§cServer-Time §8» §7" + calendar.get(Calendar.HOUR_OF_DAY) + ":" + calendar.get(Calendar.MINUTE));
                mi.sendMessage(sender, "§cYour UUID (Intern) §8» §7" + mi.getInternUUID(sender));
                if (input.hasNext()) {
                    String target = input.getPrimaryData();
                    mi.sendMessage(sender, "§c" + target + "'s UUID (Intern) §8» §7" + mi.getInternUUID(target));
                    mi.sendMessage(sender, "§c" + target + "'s UUID (Fetched) §8» §7" + UUIDManager.get().getUUID(target));
                }
            },
            null,
            "systemPrefs"),

    ADVANCED_BAN(null,
            ".*",
            input -> {
                MethodInterface mi = Universal.get().getMethods();
                Object sender = input.getSender();
                if (input.hasNext()) {
                    if (input.getPrimaryData().equals("reload")) {
                        if (Universal.get().hasPerms(sender, "ab.reload")) {
                            mi.loadFiles();
                            mi.sendMessage(sender, "§a§lAdvancedBan §8§l» §7Reloaded!");
                        } else {
                            MessageManager.sendMessage(sender, "General.NoPerms", true);
                        }
                        return;
                    } else if (input.getPrimaryData().equals("help")) {
                        if (Universal.get().hasPerms(sender, "ab.help")) {
                            mi.sendMessage(sender, "§8");
                            mi.sendMessage(sender, "§c§lAdvancedBan §7Command-Help");
                            mi.sendMessage(sender, "§8");
                            mi.sendMessage(sender, "§c/ban [Name] [Reason/@Layout]");
                            mi.sendMessage(sender, "§8» §7Ban a user permanently");
                            mi.sendMessage(sender, "§c/banip [Name/IP] [Reason/@Layout]");
                            mi.sendMessage(sender, "§8» §7Ban a user by IP");
                            mi.sendMessage(sender, "§c/tempban [Name] [Xmo/Xd/Xh/Xm/Xs/#TimeLayout] [Reason/@Layout]");
                            mi.sendMessage(sender, "§8» §7Ban a user temporary");
                            mi.sendMessage(sender, "§c/mute [Name] [Reason/@Layout]");
                            mi.sendMessage(sender, "§8» §7Mute a user permanently");
                            mi.sendMessage(sender, "§c/tempmute [Name] [Xmo/Xd/Xh/Xm/Xs/#TimeLayout] [Reason/@Layout]");
                            mi.sendMessage(sender, "§8» §7Mute a user temporary");
                            mi.sendMessage(sender, "§c/warn [Name] [Reason/@Layout]");
                            mi.sendMessage(sender, "§8» §7Warn a user permanently");
                            mi.sendMessage(sender, "§c/tempwarn [Name] [Xmo/Xd/Xh/Xm/Xs/#TimeLayout] [Reason/@Layout]");
                            mi.sendMessage(sender, "§8» §7Warn a user temporary");
                            mi.sendMessage(sender, "§c/kick [Name] [Reason/@Layout]");
                            mi.sendMessage(sender, "§8» §7Kick a user");
                            mi.sendMessage(sender, "§c/unban [Name/IP]");
                            mi.sendMessage(sender, "§8» §7Unban a user");
                            mi.sendMessage(sender, "§c/unmute [Name]");
                            mi.sendMessage(sender, "§8» §7Unmute a user");
                            mi.sendMessage(sender, "§c/unwarn [ID] or /unwarn clear [Name]");
                            mi.sendMessage(sender, "§8» §7Deletes a warn");
                            mi.sendMessage(sender, "§c/change-reason [ID or ban/mute USER] [New reason]");
                            mi.sendMessage(sender, "§8» §7Changes the reason of a punishment");
                            mi.sendMessage(sender, "§c/unpunish [ID]");
                            mi.sendMessage(sender, "§8» §7Deletes a punishment by ID");
                            mi.sendMessage(sender, "§c/banlist <Page>");
                            mi.sendMessage(sender, "§8» §7See all punishments");
                            mi.sendMessage(sender, "§c/history [Name/IP] <Page>");
                            mi.sendMessage(sender, "§8» §7See a users history");
                            mi.sendMessage(sender, "§c/warns [Name] <Page>");
                            mi.sendMessage(sender, "§8» §7See your or a users wa");
                            mi.sendMessage(sender, "§c/check [Name]");
                            mi.sendMessage(sender, "§8» §7Get all information about a user");
                            mi.sendMessage(sender, "§c/AdvancedBan <reload/help>");
                            mi.sendMessage(sender, "§8» §7Reloads the plugin or shows help page");
                            mi.sendMessage(sender, "§8");
                        } else {
                            MessageManager.sendMessage(sender, "General.NoPerms", true);
                        }
                        return;
                    }
                }


                mi.sendMessage(sender, "§8§l§m-=====§r §c§lAdvancedBan v2 §8§l§m=====-§r ");
                mi.sendMessage(sender, "  §cDev §8• §7Leoko");
                mi.sendMessage(sender, "  §cStatus §8• §a§oStable");
                mi.sendMessage(sender, "  §cVersion §8• §7" + mi.getVersion());
                mi.sendMessage(sender, "  §cLicense §8• §7Public");
                mi.sendMessage(sender, "  §cStorage §8• §7" + (DatabaseManager.get().isUseMySQL() ? "MySQL (external)" : "HSQLDB (local)"));
                mi.sendMessage(sender, "  §cServer §8• §7" + (Universal.get().isBungee() ? "Bungeecord" : "Spigot/Bukkit"));
                if (Universal.get().isBungee()) {
                    mi.sendMessage(sender, "  §cRedisBungee §8• §7" + (Universal.get().useRedis() ? "true" : "false"));
                }
                mi.sendMessage(sender, "  §cUUID-Mode §8• §7" + UUIDManager.get().getMode());
                mi.sendMessage(sender, "  §cPrefix §8• §7" + (mi.getBoolean(mi.getConfig(), "Disable Prefix", false) ? "" : MessageManager.getMessage("General.Prefix")));
                mi.sendMessage(sender, "§8§l§m-=========================-§r ");
            },
            null,
            "advancedban");

    @Getter
    private final String permission;
    private final Predicate<String[]> syntaxValidator;
    private final Consumer<CommandInput> commandHandler;
    @Getter
    private final String usagePath;
    @Getter
    private final String[] names;

    Command(String permission, Predicate<String[]> syntaxValidator,
            Consumer<CommandInput> commandHandler, String usagePath, String... names) {
        this.permission = permission;
        this.syntaxValidator = syntaxValidator;
        this.commandHandler = commandHandler;
        this.usagePath = usagePath;
        this.names = names;
    }

    Command(String permission, String regex, Consumer<CommandInput> commandHandler,
            String usagePath, String... names) {
        this(permission, (args) -> String.join(" ", args).matches(regex), commandHandler, usagePath, names);
    }

    public boolean validateArguments(String[] args) {
        return syntaxValidator.test(args);
    }

    public void execute(AdvancedBanCommandSender player, String[] args) {
        commandHandler.accept(new CommandInput(player, args));
    }

    public static Command getByName(String name) {
        String lowerCase = name.toLowerCase();
        for (Command command : values()) {
            for (String s : command.names) {
                if (s.equals(lowerCase))
                    return command;
            }
        }
        return null;
    }

    @Getter
    @AllArgsConstructor
    public class CommandInput {
        private AdvancedBanCommandSender sender;
        private String[] args;

        public String getPrimary() {
            return args.length == 0 ? null : args[0];
        }

        String getPrimaryData() {
            return getPrimary().toLowerCase();
        }

        public void removeArgument(int index) {
            args = (String[]) ArrayUtils.remove(args, index);
        }

        public void next() {
            args = (String[]) ArrayUtils.remove(args, 0);
        }

        public boolean hasNext() {
            return args.length > 0;
        }
    }
}
