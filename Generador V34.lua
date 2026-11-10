--[[
  Generador_V34.lua
  Genera patterns.h para el módulo Zygisk.
--]]

local INPUT_FILE  = (arg and arg[1]) or "CODIGOS.txt"
local OUTPUT_FILE = (arg and arg[2]) or "zygisk-module/src/patterns.h"

local function trim(s) return s:match("^%s*(.-)%s*$") end
local function parse_hex(s)
    s = trim(s)
    if s == "" then error("cadena hexadecimal vacía", 2) end
    if s:find("[^%x%s]") then error("carácter no hexadecimal en: " .. s, 2) end
    local compact = s:gsub("%s+", "")
    if #compact == 0 or (#compact % 2) ~= 0 then error("cantidad impar de dígitos hexadecimales en: " .. s, 2) end
    local bytes = {}
    for i = 1, #compact, 2 do bytes[#bytes + 1] = tonumber(compact:sub(i, i + 1), 16) end
    return bytes
end

local function to_c(bytes)
    local t = {}
    for _, b in ipairs(bytes) do t[#t+1] = ("0x%02x"):format(b) end
    return "{" .. table.concat(t, ",") .. "}"
end

local function parse_file(filename)
    local f = assert(io.open(filename, "r"), "ERROR: No se puede abrir '" .. filename .. "'")
    local mods, cur, vdata = {}, nil, {}
    local mod_names = {}
    
    local function save()
        if cur then 
            if mod_names[cur.name] then f:close(); error("Mod duplicado: '" .. cur.name .. "'", 0) end
            mod_names[cur.name] = true
            cur.versions = vdata
            mods[#mods+1] = cur 
        end
        cur = nil; vdata = {}
    end
    
    for line in f:lines() do
        if line:match("^%s*$") or line:match("^%s*%-%-") then
        elseif line:lower():match("juntos") and cur then cur.joint = true
        else
            if not line:match("local%s") and not line:match("=") then
                local hdr = line:match("^%s*(.-)%s*:%s*$")
                if hdr and #trim(hdr) > 1 then save(); cur = { name=trim(hdr), joint=false } end
            else
                local vname, content = line:match('local%s+([%w_]+)%s*=%s*{(.-)}')
                if vname and content and cur then
                    local vn  = tonumber(vname:match("^v(%d+)_"))
                    local typ = vname:match("_(buscar)$") or vname:match("_(editar)$") or vname:match("_(ancla)$")
                    if vn and typ then
                        if not vdata[vn] then vdata[vn] = {buscar={}, editar={}, ancla={}} end
                        for item in content:gmatch('"([^"]*)"') do
                            local ok, bytes = pcall(parse_hex, item)
                            if not ok then f:close(); error("ERROR en: " .. item, 0) end
                            if typ == "ancla" then vdata[vn].ancla = bytes
                            else vdata[vn][typ][#vdata[vn][typ] + 1] = bytes end
                        end
                    end
                end
            end
        end
    end
    f:close(); save()
    return mods
end

local function generate_h(mods)
    local out = {}
    local function w(s) out[#out+1] = s end
    
    w("#pragma once")
    w("#include <stdint.h>")
    w("#include <stddef.h>\n")
    w("typedef struct { uint8_t buscar[128]; uint8_t editar[128]; int len; } Par;")
    w("typedef struct { const char* nombre; Par* pares; int n_pares; const uint8_t* ancla; int ancla_len; } Mod;\n")
    
    local mod_entries = {}
    for mi, mod in ipairs(mods) do
        local sorted = {}
        for vn, vd in pairs(mod.versions) do sorted[#sorted+1] = {n=vn, d=vd} end
        table.sort(sorted, function(a,b) return a.n < b.n end)
        
        local all_pares = {}
        local ancla_sym = "NULL"
        local ancla_len = "0"
        
        for _, vi in ipairs(sorted) do
            local vd = vi.d
            if vd.ancla and #vd.ancla > 0 then
                ancla_sym = ("mod%d_ancla"):format(mi)
                ancla_len = tostring(#vd.ancla)
                w(("static const uint8_t %s[%d] = %s;"):format(ancla_sym, #vd.ancla, to_c(vd.ancla)))
            end
            for bi, bpat in ipairs(vd.buscar) do
                local epat = (#vd.editar == 1) and vd.editar[1] or vd.editar[bi]
                all_pares[#all_pares+1] = {bpat, epat}
            end
        end
        
        local pares_sym = ("mod%d_pares"):format(mi)
        w(("static Par %s[] = {"):format(pares_sym))
        for _, p in ipairs(all_pares) do
            w(("    {{%s}, {%s}, %d},"):format(table.concat(p[1], ","), table.concat(p[2], ","), #p[1]))
        end
        w("};")
        mod_entries[#mod_entries+1] = ('    {"%s", %s, %d, %s, %s},'):format(mod.name, pares_sym, #all_pares, ancla_sym, ancla_len)
    end
    
    w("\nstatic Mod MODS[] = {")
    for _, e in ipairs(mod_entries) do w(e) end
    w("};\n#define N_MODS (sizeof(MODS)/sizeof(MODS[0]))")
    return table.concat(out, "\n")
end

local mods = parse_file(INPUT_FILE)
local h_src = generate_h(mods)
local fout = assert(io.open(OUTPUT_FILE, "w"), "No se puede escribir '" .. OUTPUT_FILE .. "'")
fout:write(h_src)
fout:close()
print("[+] Generado: " .. OUTPUT_FILE)