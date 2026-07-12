package com.aicode.feature.agent.domain.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInSafeCommandsTest {

    private fun safe(command: String): Boolean =
        BuiltInSafeCommands.isSafe(ShellCommandParser.analyze(command).segments.first())

    @Test
    fun readOnlyInspection_isSafe() {
        assertTrue(safe("ls"))
        assertTrue(safe("ls -la"))
        assertTrue(safe("pwd"))
        assertTrue(safe("cat README.md"))
        assertTrue(safe("grep -rn foo src/"))
        assertTrue(safe("rg pattern"))
        assertTrue(safe("wc -l file.txt"))
        assertTrue(safe("stat ."))
        assertTrue(safe("du -sh ."))
        assertTrue(safe("echo hello"))
        assertTrue(safe("which node"))
        assertTrue(safe("ps aux"))
        assertTrue(safe("top -n 1"))
        assertTrue(safe("free -m"))
        assertTrue(safe("htop"))
        assertTrue(safe("clear"))
        assertTrue(safe("history"))
    }

    @Test
    fun gitReadOnlySubcommands_areSafe() {
        assertTrue(safe("git status"))
        assertTrue(safe("git status -s"))
        assertTrue(safe("git log --oneline -5"))
        assertTrue(safe("git diff HEAD~1"))
        assertTrue(safe("git show abc1234"))
        assertTrue(safe("git blame src/Main.kt"))
        assertTrue(safe("git rev-parse HEAD"))
        assertTrue(safe("git ls-files"))
        assertTrue(safe("git config --get user.name"))
    }

    @Test
    fun gitWriteSubcommands_areNotSafe() {
        // 写操作子命令不在白名单，必须弹窗——与「git pull/clone 单独授权」一致。
        assertFalse(safe("git pull"))
        assertFalse(safe("git clone https://x/y"))
        assertFalse(safe("git push"))
        assertFalse(safe("git commit -m msg"))
        assertFalse(safe("git add ."))
        assertFalse(safe("git reset --hard"))
        assertFalse(safe("git checkout main"))
        assertFalse(safe("git branch -D feature"))
        assertFalse(safe("git tag v1.0"))
        assertFalse(safe("git merge feature"))
    }

    @Test
    fun mutatingOrExecutingCommands_areNotSafe() {
        // 派生执行/可写/网络命令一律不放行，避免 env rm -rf / 之类被前缀规则误放。
        assertFalse(safe("rm -rf /"))
        assertFalse(safe("env rm -rf /"))   // env 可派生子进程，绝不收
        assertFalse(safe("command rm"))      // command 可派生
        assertFalse(safe("xargs rm"))
        assertFalse(safe("find . -exec rm {} \\;"))
        assertFalse(safe("sed -i s/a/b/ f"))
        assertFalse(safe("tee /etc/hosts"))
        assertFalse(safe("cp a b"))
        assertFalse(safe("mv a b"))
        assertFalse(safe("chmod 777 ."))
        assertFalse(safe("curl http://x"))
        assertFalse(safe("wget http://x"))
        assertFalse(safe("node -e code"))
        assertFalse(safe("python script.py"))
        assertFalse(safe("dd if=/dev/zero of=x"))
    }

    @Test
    fun leadingFlagOnGit_isNotSafe() {
        // git --version 不匹配任何只读子命令前缀 → 不内置放行（无害但不在白名单，弹窗一次）。
        assertFalse(safe("git --version"))
    }
}
