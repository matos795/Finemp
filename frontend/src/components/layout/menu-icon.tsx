import { menuIcons, type MenuIconName } from "./menu-icons"

export function MenuIcon({
    name,
}: {
    name: MenuIconName
}) {
    return (
        <svg
            width="18"
            height="18"
            viewBox="0 0 20 20"
            aria-hidden="true"
            dangerouslySetInnerHTML={{ __html: menuIcons[name] }}
        />
    )
}
